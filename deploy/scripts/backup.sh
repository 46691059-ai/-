#!/bin/sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
deploy_dir="$(dirname "$script_dir")"
project_dir="$(dirname "$deploy_dir")"
compose_file="$deploy_dir/docker-compose.yml"
env_file="$deploy_dir/.env.production"

if [ ! -f "$env_file" ]; then
  echo "缺少生产环境文件：$env_file" >&2
  exit 1
fi

backup_root="${BACKUP_DIR:-$deploy_dir/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-30}"
timestamp="$(date '+%Y%m%d_%H%M%S')"
backup_dir="$backup_root/$timestamp"

case "$backup_root" in
  ""|"/"|"$project_dir")
    echo "拒绝使用不安全的备份目录：$backup_root" >&2
    exit 1
    ;;
esac

umask 077
mkdir -p "$backup_dir"

dc() {
  docker compose --env-file "$env_file" -f "$compose_file" "$@"
}

echo "[$(date -Iseconds)] 开始备份 MySQL"
mysql_file="$backup_dir/mysql_${timestamp}.sql.gz"
dc exec -T mysql sh -ec '
  password="$(cat /run/secrets/mysql_app_password)"
  exec mysqldump \
    --host=127.0.0.1 \
    --user="$MYSQL_USER" \
    --password="$password" \
    --single-transaction \
    --quick \
    --routines \
    --events \
    --triggers \
    --hex-blob \
    --set-gtid-purged=OFF \
    --no-tablespaces \
    "$MYSQL_DATABASE"
' | gzip -9 > "$mysql_file"

gzip -t "$mysql_file"

echo "[$(date -Iseconds)] 开始备份 Redis"
dc exec -T redis sh -ec '
  password="$(cat /run/secrets/redis_password)"
  redis-cli --no-auth-warning --pass "$password" BGSAVE >/dev/null
  while [ "$(redis-cli --no-auth-warning --pass "$password" \
      INFO persistence | sed -n "s/^rdb_bgsave_in_progress:\([01]\).*/\1/p" | tr -d "\r")" = "1" ]; do
    sleep 1
  done
  test "$(redis-cli --no-auth-warning --pass "$password" \
      INFO persistence | sed -n "s/^rdb_last_bgsave_status:\(.*\)/\1/p" | tr -d "\r")" = "ok"
'

redis_container="$(dc ps -q redis)"
redis_raw="$backup_dir/redis_${timestamp}.rdb"
docker cp "$redis_container:/data/dump.rdb" "$redis_raw"
gzip -9 "$redis_raw"
redis_file="${redis_raw}.gz"
gzip -t "$redis_file"

(
  cd "$backup_dir"
  sha256sum ./*.gz > SHA256SUMS
)

cat > "$backup_dir/manifest.txt" <<EOF
backup_time=$timestamp
project=enterprise-platform
mysql_file=$(basename "$mysql_file")
redis_file=$(basename "$redis_file")
retention_days=$retention_days
EOF

find "$backup_root" -type f -mtime "+$retention_days" -delete
find "$backup_root" -mindepth 1 -type d -empty -delete

echo "[$(date -Iseconds)] 备份完成：$backup_dir"
echo "请将该目录同步到异机或对象存储，并定期执行恢复演练。"
