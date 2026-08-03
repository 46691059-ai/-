# 国企数字化治理与经营赋能平台架构说明

版本：V1.0.0
冻结日期：2026-08-03

## 1. 总体结构

```text
enterprise-platform
├── backend/                 Java 21 / Spring Boot 3 后端
│   ├── src/main/java/cn/gov/enterprise
│   │   ├── common/          返回、异常、持久化、数据权限等公共能力
│   │   ├── config/          MyBatis Plus、Redis、Web、字段映射检查配置
│   │   ├── security/        JWT、RBAC、认证、权限缓存
│   │   └── modules/         system 与既有 project 模块
│   └── src/test/            单元与上下文测试
├── frontend/                Vue 3 / TypeScript / Vite 前端
│   └── src
│       ├── api/             后端 API 客户端
│       ├── layout/          后台统一布局
│       ├── permission/      路由守卫与按钮权限
│       ├── router/          静态路由与动态路由
│       ├── store/           Pinia 状态
│       ├── utils/           Axios、认证存储等工具
│       └── views/           基础平台页面及既有项目页面
├── database/mysql/          MySQL 初始化、迁移及验收 SQL
├── deploy/                  Docker、Nginx、MySQL、Redis、备份配置
└── docs/                    架构、规范与验收文档
```

## 2. 分层与依赖方向

后端请求按 `Controller -> Service -> Mapper -> Database` 流转。Controller 仅负责协议、校验和授权；Service 承担事务与业务规则；Mapper 只处理持久化。公共能力位于 `common`、`config`、`security`，业务代码按 `modules.<module>` 隔离。

前端按 `View/Component -> Store/API -> request -> Backend` 流转。路由守卫保证登录态，动态路由控制导航可见性，`v-permission` 控制按钮展示；后端权限校验始终是安全边界。

## 3. 基础能力

- 认证：JWT，无状态会话；令牌包含用户、版本、签发方、受众和过期时间。
- 授权：Spring Security `@PreAuthorize` + RBAC 菜单/按钮权限。
- 权限缓存：Redis 保存用户权限快照；角色权限变更和退出时清理。
- 数据权限：MyBatis Plus 拦截器统一追加 ALL、ORG、ORG_AND_CHILDREN、SELF、CUSTOM 条件。
- 数据访问：MyBatis Plus，逻辑删除、审计字段和乐观锁由统一基类承载。
- 可观测性：TraceId、结构化控制台日志、操作日志和异常日志。
- 部署：Java 21 多阶段镜像、Nginx 静态站点、MySQL 8.4、Redis 7.4、Docker Compose 内网隔离。

## 4. 安全边界

- `/auth/login`、健康和信息端点以外的接口默认要求认证。
- API 权限由后端 `@PreAuthorize` 强制执行，前端隐藏菜单不能替代授权。
- 数据查询必须在 Service 使用 `@DataScope`，并由 MyBatis Plus 拦截器执行数据隔离。
- 密码只保存 BCrypt 摘要；日志不得记录密码、完整 JWT、身份证号等敏感数据。
- 生产数据库密码、Redis 密码和 JWT 密钥通过 Docker secrets/configtree 注入。

## 5. 国产化适配边界

业务代码避免 MySQL 专属 SQL，分页、逻辑删除和映射统一由 MyBatis Plus 管理。达梦、人大金仓适配仍需提供独立方言、驱动、DDL 和兼容性回归，V1.0.0 尚未完成生产级国产数据库认证。
