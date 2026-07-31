#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "用法：CONFIRM_RESTORE=YES $0 /path/to/mysql_backup.sql.gz" >&2
  exit 1
fi
if [ "${CONFIRM_RESTORE:-NO}" != "YES" ]; then
  echo "恢复会覆盖目标库数据；确认后设置 CONFIRM_RESTORE=YES。" >&2
  exit 1
fi

backup_file="$1"
if [ ! -f "$backup_file" ]; then
  echo "备份文件不存在：$backup_file" >&2
  exit 1
fi
gzip -t "$backup_file"

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
deploy_dir="$(dirname "$script_dir")"
compose_file="$deploy_dir/docker-compose.yml"
env_file="$deploy_dir/.env.production"

gunzip -c "$backup_file" | docker compose \
  --env-file "$env_file" -f "$compose_file" exec -T mysql sh -ec '
    password="$(cat /run/secrets/mysql_app_password)"
    exec mysql \
      --host=127.0.0.1 \
      --user="$MYSQL_USER" \
      --password="$password" \
      "$MYSQL_DATABASE"
  '

echo "MySQL 恢复完成，请执行业务校验和抽样核对。"
