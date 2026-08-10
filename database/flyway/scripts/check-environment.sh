#!/usr/bin/env bash
set -euo pipefail

environment_name="${1:-dev}"
validation_phase="${2:-pre}"
case "$environment_name" in
  dev|test|preprod|prod) ;;
  *)
    echo "Unsupported environment '$environment_name'." >&2
    exit 64
    ;;
esac

case "$validation_phase" in
  pre|post) ;;
  *)
    echo "Unsupported validation phase '$validation_phase'. Use pre or post." >&2
    exit 64
    ;;
esac

require_env() {
  local variable_name="$1"
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Required environment variable '$variable_name' is not set." >&2
    exit 64
  fi
}

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_root/../../.." && pwd)"
fingerprint_sql="$repository_root/database/mysql/verification/schema_fingerprint.sql"
migration_directory="$repository_root/database/migration/mysql"
checksum_manifest="$migration_directory/SHA256SUMS"
vendor="${FLYWAY_VENDOR:-mysql}"
database_name="${DB_NAME:-enterprise_platform}"

if [[ "$vendor" != "mysql" ]]; then
  echo "Schema fingerprint implementation is currently available only for MySQL. Vendor '$vendor' requires its reviewed adapter." >&2
  exit 69
fi

require_env MYSQL_CLIENT_DEFAULTS_FILE
if [[ ! -f "$MYSQL_CLIENT_DEFAULTS_FILE" ]]; then
  echo "MYSQL_CLIENT_DEFAULTS_FILE does not exist: $MYSQL_CLIENT_DEFAULTS_FILE" >&2
  exit 66
fi
if ! command -v mysql >/dev/null 2>&1; then
  echo "The mysql client is required for version and schema-fingerprint checks." >&2
  exit 69
fi

mysql_readonly=(
  mysql
  "--defaults-extra-file=$MYSQL_CLIENT_DEFAULTS_FILE"
  --batch --raw --skip-column-names
  "--database=$database_name"
)

echo "Environment check: $environment_name"
echo "Validation phase: $validation_phase"
echo "Vendor: $vendor"
echo "Database: $database_name"

history_exists="$("${mysql_readonly[@]}" --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'")"
if [[ "$history_exists" != "1" ]]; then
  echo "flyway_schema_history is missing. Run the approved onboarding and baseline process before validate/migrate." >&2
  exit 78
fi

current_version="$("${mysql_readonly[@]}" --execute="SELECT COALESCE(version, '') FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"
if [[ -z "$current_version" ]]; then
  echo "No successful versioned or baseline record exists in flyway_schema_history." >&2
  exit 78
fi
echo "Current database version: $current_version"

failed_history_count="$("${mysql_readonly[@]}" --execute="SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0")"
if [[ "$failed_history_count" != "0" ]]; then
  echo "Flyway history contains $failed_history_count failed migration record(s)." >&2
  exit 78
fi

if [[ "$validation_phase" == "pre" ]]; then
  expected_version="${EXPECTED_SOURCE_DB_VERSION:-${EXPECTED_DB_VERSION:-}}"
  required_version_name="EXPECTED_SOURCE_DB_VERSION"
else
  expected_version="${EXPECTED_TARGET_DB_VERSION:-${EXPECTED_DB_VERSION:-}}"
  required_version_name="EXPECTED_TARGET_DB_VERSION"
fi

if [[ -n "$expected_version" && "$current_version" != "$expected_version" ]]; then
  echo "Database version mismatch during '$validation_phase' check. Expected '$expected_version', actual '$current_version'." >&2
  exit 78
fi
if [[ "$environment_name" == "preprod" || "$environment_name" == "prod" ]]; then
  if [[ -z "$expected_version" ]]; then
    echo "$required_version_name is mandatory in preprod and prod." >&2
    exit 64
  fi
fi

if [[ "$validation_phase" == "pre" ]]; then
  if [[ ! -f "$checksum_manifest" ]]; then
    echo "Migration checksum manifest is missing: $checksum_manifest" >&2
    exit 66
  fi
  (cd "$migration_directory" && sha256sum --check --strict SHA256SUMS)

  mapfile -t governed_sql < <(cd "$migration_directory" && find . -maxdepth 1 -type f -name '*.sql' -printf '%f\n' | sort)
  mapfile -t listed_sql < <(awk '!/^\s*(#|$)/ {name=$2; sub(/^\*/, "", name); print name}' "$checksum_manifest" | sort)
  if [[ "$(printf '%s\n' "${governed_sql[@]}")" != "$(printf '%s\n' "${listed_sql[@]}")" ]]; then
    echo "Governed SQL files and SHA256SUMS entries do not match." >&2
    exit 78
  fi

  echo "Migration asset policy passed: ${#listed_sql[@]} checksum(s) verified."
  echo "Running Flyway info. Pending migrations are permitted during the pre-migration policy phase."
  bash "$script_root/flyway.sh" info "$environment_name"
else
  echo "Running strict post-migration Flyway validate and info..."
  bash "$script_root/flyway.sh" validate "$environment_name"
  bash "$script_root/flyway.sh" info "$environment_name"
fi

temporary_output="$(mktemp)"
trap 'rm -f "$temporary_output"' EXIT
"${mysql_readonly[@]}" < "$fingerprint_sql" > "$temporary_output"
fingerprint="$(sha256sum "$temporary_output" | awk '{print $1}')"
echo "Schema fingerprint (SHA-256): $fingerprint"

if [[ "$validation_phase" == "pre" ]]; then
  expected_fingerprint="${EXPECTED_PRE_SCHEMA_FINGERPRINT:-${EXPECTED_SCHEMA_FINGERPRINT:-}}"
  required_fingerprint_name="EXPECTED_PRE_SCHEMA_FINGERPRINT"
else
  expected_fingerprint="${EXPECTED_POST_SCHEMA_FINGERPRINT:-${EXPECTED_SCHEMA_FINGERPRINT:-}}"
  required_fingerprint_name="EXPECTED_POST_SCHEMA_FINGERPRINT"
fi
if [[ -n "$expected_fingerprint" && "$fingerprint" != "${expected_fingerprint,,}" ]]; then
  echo "Schema fingerprint mismatch during '$validation_phase' check. Expected '$expected_fingerprint', actual '$fingerprint'." >&2
  exit 78
fi
if [[ "$environment_name" == "preprod" || "$environment_name" == "prod" ]]; then
  if [[ -z "$expected_fingerprint" ]]; then
    echo "$required_fingerprint_name is mandatory in preprod and prod." >&2
    exit 64
  fi
fi

echo "Environment '$validation_phase' validation passed. No repair or migration was executed by this check."
