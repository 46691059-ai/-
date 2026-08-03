# Sprint 1-5 操作日志中心验收报告

## 验收结论

操作日志中心后端、前端、权限初始化及真实 MySQL 链路验收通过。验收期间未修改数据库表结构，也未修改项目生命周期业务代码。

## 自动化测试

| 范围 | 结果 |
|---|---|
| Java 21 主源码编译 | 141 个源码文件通过 |
| 后端测试 | 47 项通过，0 失败，0 错误 |
| 前端测试 | 21 项通过 |
| 前端类型检查与生产构建 | 通过 |
| `git diff --check` | 通过 |

## 真实数据库验收

- MySQL：8.0.46 临时隔离实例。
- `16_sprint_1_log_center.sql`连续执行两次成功，无重复数据。
- 日志权限：4 条；日志菜单：1 个页面节点、3 个按钮节点。
- 超级管理员日志权限关系：4 条；角色菜单关系：4 条。
- Spring Boot 使用 Java 21 连接真实 MySQL 启动成功。
- 管理员登录、用户新增、菜单更新、参数转换异常及日志分页查询均通过。
- 实际日志包含`LOGIN_SUCCESS`、`OPERATION`、`ERROR`三类记录，且均有关联 TraceId。
- 对审计数据扫描后，测试密码原文、完整手机号和 JWT 特征串命中数均为 0。

## 数据库检查

`sys_log`缺少独立的`username`、`log_type`、`module_name`、`request_params`和`response_result`字段。本 Sprint 使用`operation`标准化编码及`remark`脱敏上下文兼容，详细结论见`sys-log-field-gap-report.md`。

现有索引适合用户/状态结合时间及 TraceId 查询；仅按时间、类型、模块或上下文用户名查询在大数据量下可能发生扫描。本 Sprint 按要求不执行 DDL。

## 验收环境说明

临时 Windows Redis 5 的`INFO`内容与当前 Spring Data Redis 健康检查解析存在兼容差异，因此 Actuator 聚合健康状态显示`DOWN`；JWT撤销检查、权限缓存读写和全部业务验收请求均正常。生产编排使用 Redis 7.4 Alpine，不受该临时验收组件差异影响。
