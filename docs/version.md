# 版本信息

版本：`v1.0.0-foundation`

版本类型：基础平台冻结版本，非 production 标记。

## 包含模块

- 系统管理
  - 用户管理
  - 组织管理
  - 角色管理
  - 菜单管理
- 权限体系
  - JWT认证
  - RBAC功能权限
  - 动态路由与按钮权限
  - Project统一访问策略
- 数据权限
  - ALL
  - ORG
  - ORG_AND_CHILDREN
  - SELF
  - CUSTOM
- 日志体系
  - 操作日志
  - 登录日志
  - 异常日志
  - TraceId链路
  - 敏感信息脱敏

## 冻结范围

- 冻结当前基础平台开发基线。
- 不代表 Docker、Linux、Nginx、HTTPS、备份或 Redis 持久化已经完成生产验收。
- 后续变更通过新 Sprint 或修复分支提交，不移动本标签。

## 下一阶段

Sprint 2：项目生命周期管理。

进入 Sprint 2 前，先依据 Project 安全规范处理项目历史字段映射、数据权限真实数据库集成测试和既有页面技术债。
