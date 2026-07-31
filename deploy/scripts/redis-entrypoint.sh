#!/bin/sh
set -eu

password_file="/run/secrets/redis_password"
acl_file="/run/redis-auth/users.acl"

if [ ! -s "$password_file" ]; then
  echo "Redis password secret is missing or empty" >&2
  exit 1
fi

password="$(cat "$password_file")"
case "$password" in
  *[!A-Za-z0-9]*)
    echo "Redis password must contain only letters and digits" >&2
    exit 1
    ;;
esac

umask 077
printf 'user default on >%s ~* &* +@all\n' "$password" > "$acl_file"
unset password
if [ "$(id -u)" = "0" ]; then
  chown redis:redis "$acl_file"
fi

exec /usr/local/bin/docker-entrypoint.sh redis-server /etc/redis/redis.conf \
  --aclfile "$acl_file" \
  --maxmemory "${REDIS_MAXMEMORY:-512mb}" \
  "$@"
