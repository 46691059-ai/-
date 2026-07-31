#!/bin/sh
set -eu

script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
deploy_dir="$(dirname "$script_dir")"
secret_dir="$deploy_dir/secrets"

umask 077
mkdir -p "$secret_dir"

generate_secret() {
  target="$1"
  bytes="$2"
  if [ -e "$target" ]; then
    echo "保留已有密钥：$target"
    return
  fi
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$bytes" > "$target"
  else
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n' > "$target"
    printf '\n' >> "$target"
  fi
  chmod 600 "$target"
  echo "已生成：$target"
}

generate_secret "$secret_dir/mysql_app_password.txt" 24
generate_secret "$secret_dir/mysql_root_password.txt" 32
generate_secret "$secret_dir/redis_password.txt" 24
generate_secret "$secret_dir/jwt_secret.txt" 48

if [ ! -e "$deploy_dir/.env.production" ]; then
  cp "$deploy_dir/.env.production.example" "$deploy_dir/.env.production"
  chmod 600 "$deploy_dir/.env.production"
  echo "已生成 $deploy_dir/.env.production，请修改域名和资源参数。"
fi
