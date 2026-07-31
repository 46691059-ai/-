# enterprise-platform Linux 生产部署方案

## 1. 部署拓扑

```text
Internet / 政务外网
        |
   HTTPS LB/WAF
        |
 Nginx frontend :8080
        |
 Spring Boot backend :8080
      /        \
 MySQL 8.4   Redis 7.4
```

- 宿主机只开放 Nginx 映射端口。
- 后端只加入 `edge`、`data` 两个容器网络。
- MySQL、Redis 只加入隔离的 `data` 网络，不映射宿主机端口。
- 推荐由已有负载均衡、WAF 或网关终止 TLS；直接暴露服务器时，应在外层配置 HTTPS。

## 2. 服务器基线

建议起步配置：8 核 CPU、16 GB 内存、200 GB SSD，生产数据盘与系统盘分离。

Linux 服务器需安装：

- Docker Engine 及 Docker Compose 插件；
- `openssl`、`gzip`、`sha256sum`；
- NTP/chrony 时间同步；
- 仅允许运维网段访问 SSH。

防火墙只开放 `22/tcp`（限制来源）、`80/tcp` 和 `443/tcp`。不要开放
`3306/tcp`、`6379/tcp`、`8080/tcp`。

## 3. 首次部署

```bash
cd /opt/enterprise-platform

chmod +x deploy/scripts/*.sh
./deploy/scripts/init-secrets.sh

vi deploy/.env.production

docker compose \
  --env-file deploy/.env.production \
  -f deploy/docker-compose.yml \
  config

docker compose \
  --env-file deploy/.env.production \
  -f deploy/docker-compose.yml \
  build --pull

docker compose \
  --env-file deploy/.env.production \
  -f deploy/docker-compose.yml \
  up -d
```

查看状态：

```bash
docker compose \
  --env-file deploy/.env.production \
  -f deploy/docker-compose.yml \
  ps

curl -fsS http://127.0.0.1/healthz
```

首次创建 MySQL 数据卷时，`database/mysql` 下的 SQL 会按文件名顺序执行。
已有数据库卷不会再次执行初始化脚本，升级时必须在备份后由 DBA 单独执行新增迁移。

## 4. 镜像与发布

- 后端使用 Maven + JDK 21 多阶段构建，运行镜像仅包含 JRE。
- 前端使用 Node 构建，最终由非 root Nginx 提供静态资源。
- 发布镜像必须使用不可变版本号或镜像摘要，禁止复用 `latest`。
- 推荐在 CI 中运行后端测试、前端测试、依赖漏洞扫描和镜像扫描后再推送镜像仓库。

滚动更新单机实例：

```bash
docker compose \
  --env-file deploy/.env.production \
  -f deploy/docker-compose.yml \
  pull

docker compose \
  --env-file deploy/.env.production \
  -f deploy/docker-compose.yml \
  up -d --remove-orphans
```

单机 Compose 更新存在短暂中断。对连续性要求较高时，应部署两台应用节点并由外部
负载均衡逐台摘流更新，MySQL 和 Redis 使用独立高可用集群或国产数据库服务。

## 5. MySQL

- 数据持久化到 `mysql_data` 命名卷。
- 使用 `utf8mb4`、严格 SQL 模式、行格式二进制日志。
- `innodb_flush_log_at_trx_commit=1`、`sync_binlog=1` 优先保证事务持久性。
- 保留 7 天 binlog，为时间点恢复预留条件。
- 慢查询阈值为 1 秒，`mysql-slowlog` 旁车将慢查询输出到 Docker 日志。
- `innodb_buffer_pool_size=1G` 是起步值，应按服务器内存和监控结果调整。

## 6. Redis

- Redis 不对宿主机开放端口。
- 密码由 Docker secret 提供，并动态生成 ACL 文件。
- 同时启用 AOF `everysec` 与 RDB 快照。
- `maxmemory-policy=noeviction`，避免静默淘汰 JWT 撤销信息或安全缓存。
- Redis 只作为缓存与令牌撤销存储，不能作为唯一业务数据源。

## 7. 数据备份

执行一次备份：

```bash
BACKUP_RETENTION_DAYS=30 ./deploy/scripts/backup.sh
```

建议 root crontab：

```cron
15 2 * * * cd /opt/enterprise-platform && BACKUP_DIR=/data/enterprise-backups BACKUP_RETENTION_DAYS=30 ./deploy/scripts/backup.sh >> /var/log/enterprise-platform/backup.log 2>&1
```

备份内容：

- MySQL 使用 `--single-transaction` 生成一致性逻辑备份；
- Redis 触发 `BGSAVE` 后复制 RDB；
- 每次备份生成 SHA-256 校验文件；
- 本机保留 30 天。

至少采用“本机一份、异机一份、离线或对象存储一份”的 3-2-1 策略。binlog 应持续同步
到异机存储，以便进行时间点恢复。每季度在隔离环境执行一次恢复演练并记录 RTO/RPO。

MySQL 恢复示例：

```bash
CONFIRM_RESTORE=YES \
  ./deploy/scripts/restore-mysql.sh \
  /data/enterprise-backups/20260801_021500/mysql_20260801_021500.sql.gz
```

## 8. 日志管理

- Spring Boot `prod` 环境只输出控制台日志，包含时间、线程、日志级别和 `traceId`。
- Nginx 访问日志为 JSON，并记录响应时间、上游时间和链路标识。
- MySQL 慢查询通过 `mysql-slowlog` 容器输出。
- 所有容器使用 Docker `local` 日志驱动，默认单文件 20 MB、保留 10 个文件。
- 备份任务日志由 `deploy/logrotate/enterprise-platform` 每日轮转并保留 30 份。

安装宿主机轮转规则：

```bash
mkdir -p /var/log/enterprise-platform
cp deploy/logrotate/enterprise-platform /etc/logrotate.d/enterprise-platform
```

生产环境建议将 Docker 日志进一步采集到 Loki、ELK/OpenSearch 或国产日志平台，并对以下事件告警：

- 容器重启或健康检查失败；
- HTTP 5xx、认证失败和权限拒绝突增；
- MySQL 慢查询、连接数或磁盘空间异常；
- Redis 内存接近上限、AOF/RDB 持久化失败；
- 备份超过 26 小时未成功或校验失败。

## 9. 日常检查

```bash
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml ps
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml logs --since 30m backend
docker compose --env-file deploy/.env.production -f deploy/docker-compose.yml logs --since 30m mysql-slowlog
docker system df
df -h
```

密钥轮换、数据库迁移、恢复操作必须纳入变更审批并保留审计记录。
