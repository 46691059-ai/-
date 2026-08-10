#!/usr/bin/env bash
set -euo pipefail

environment_name="${1:-dev}"
case "$environment_name" in
  dev|test|preprod|prod) ;;
  *)
    echo "Unsupported environment '$environment_name'." >&2
    exit 64
    ;;
esac

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo 'Release stage 1/3: pre-migration policy checks'
bash "$script_root/check-environment.sh" "$environment_name" pre

echo 'Release stage 2/3: Flyway migrate'
bash "$script_root/flyway.sh" migrate "$environment_name"

echo 'Release stage 3/3: strict post-migration validation'
bash "$script_root/check-environment.sh" "$environment_name" post

echo 'Flyway release flow completed successfully.'
