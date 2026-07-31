# enterprise-platform

国企数字化治理与经营赋能平台企业级工程骨架。

当前版本已完成通用基础设施、系统基础数据库以及项目全生命周期模块。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.9、MyBatis-Plus、Spring Security、JWT
- 数据：MySQL 8、Redis
- 前端：Vue 3、TypeScript、Vite、Element Plus、ECharts
- 构建：Maven、npm

## 项目结构

```text
enterprise-platform/
├── pom.xml
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/cn/gov/enterprise/
│       │   │   ├── EnterprisePlatformApplication.java
│       │   │   ├── common/
│       │   │   │   ├── api/
│       │   │   │   ├── exception/
│       │   │   │   └── web/
│       │   │   ├── config/
│       │   │   ├── security/
│       │   │   └── modules/
│       │   └── resources/
│       │       ├── mapper/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       └── logback-spring.xml
│       └── test/
├── frontend/
│   ├── src/
│   │   ├── router/
│   │   ├── styles/
│   │   ├── types/
│   │   ├── utils/
│   │   └── views/
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── database/
├── docs/
└── deploy/
```

## 配置变量

| 变量 | 说明 |
|---|---|
| `DB_URL` | MySQL JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号与密码 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥，生产环境至少 32 个随机字节 |
| `JWT_AUDIENCE` / `JWT_ISSUER` | JWT 接收方与签发方 |
| `JWT_EXPIRATION_SECONDS` | JWT 有效期 |
| `ALLOWED_ORIGINS` | 前端跨域来源，多个值用逗号分隔 |
| `LOG_PATH` | 日志目录 |

所有环境都必须注入随机 `JWT_SECRET`，不提供可被误用的默认签名密钥。

数据库按顺序执行：

1. `database/mysql/V1.0.0__system_base.sql`
2. `database/mysql/V1.1.0__project_lifecycle.sql`
3. `database/mysql/V1.2.0__security_and_project_hardening.sql`

## 启动

```shell
# 基础设施
docker compose --env-file deploy/.env -f deploy/docker-compose.infra.yml up -d

# 后端
mvn -pl backend spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

后端健康检查：`GET http://localhost:8080/api/actuator/health`。

## 生产部署

Linux + Docker Compose 生产方案见
[`docs/production-deployment.md`](docs/production-deployment.md)。
完整编排文件位于 `deploy/docker-compose.yml`，包含 Nginx、后端、MySQL、Redis、
健康检查、Docker secrets、自动备份和日志轮转配置。

## 分支与发布

采用 `main / develop / feature/* / hotfix/*` 分支模型。项目模块在
`feature/project-module` 开发，测试通过后合并到 `develop`，集成验收后合并
`main` 并使用版本标签部署生产。详见
[`docs/git-branching-strategy.md`](docs/git-branching-strategy.md)。
