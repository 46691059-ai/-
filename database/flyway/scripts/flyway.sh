#!/usr/bin/env bash
set -euo pipefail

command_name="${1:-validate}"
environment_name="${2:-dev}"

case "$command_name" in
  info|validate|migrate|baseline) ;;
  clean|repair)
    echo "Command '$command_name' is prohibited by the repository wrapper." >&2
    exit 64
    ;;
  *)
    echo "Unsupported command '$command_name'. Use info, validate, migrate, or baseline." >&2
    exit 64
    ;;
esac

case "$environment_name" in
  dev|test|preprod|prod) ;;
  *)
    echo "Unsupported environment '$environment_name'." >&2
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
vendor="${FLYWAY_VENDOR:-mysql}"

case "$vendor" in
  mysql|dm|kingbase) ;;
  *)
    echo "Unsupported FLYWAY_VENDOR '$vendor'." >&2
    exit 64
    ;;
esac

migration_directory="$repository_root/database/migration/$vendor"
if [[ ! -d "$migration_directory" ]]; then
  echo "Governed migration directory does not exist: $migration_directory" >&2
  exit 66
fi

require_env FLYWAY_IMAGE
require_env FLYWAY_URL
require_env FLYWAY_USER
require_env FLYWAY_PASSWORD

if [[ "$command_name" == "migrate" || "$command_name" == "baseline" ]]; then
  expected_confirmation="$environment_name:$command_name"
  if [[ "${CONFIRM_FLYWAY_MUTATION:-}" != "$expected_confirmation" ]]; then
    echo "Mutating command blocked. Set CONFIRM_FLYWAY_MUTATION='$expected_confirmation' for this invocation." >&2
    exit 77
  fi
  if [[ "$environment_name" == "preprod" || "$environment_name" == "prod" ]]; then
    require_env CHANGE_TICKET
  fi
fi

docker_arguments=(
  run --rm
  --mount "type=bind,src=$migration_directory,dst=/flyway/sql,readonly"
  --env FLYWAY_URL
  --env FLYWAY_USER
  --env FLYWAY_PASSWORD
)

if [[ -n "${FLYWAY_DOCKER_NETWORK:-}" ]]; then
  docker_arguments+=(--network "$FLYWAY_DOCKER_NETWORK")
fi

docker_arguments+=(
  "$FLYWAY_IMAGE"
  -locations=filesystem:/flyway/sql
  -table=flyway_schema_history
  -validateMigrationNaming=true
  -validateOnMigrate=true
  -baselineOnMigrate=false
  -cleanDisabled=true
  -outOfOrder=false
  -mixed=false
  -connectRetries=3
  "$command_name"
)

echo "Flyway command: $command_name"
echo "Environment: $environment_name"
echo "Vendor: $vendor"
echo "Migration directory: $migration_directory"
echo "Image: $FLYWAY_IMAGE"

docker "${docker_arguments[@]}"
