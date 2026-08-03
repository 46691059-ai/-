# 接口开发规范

版本：V1.0.0

## 1. 协议

- 基础路径：`/api`。
- 资源查询使用 GET，新增使用 POST，整体修改使用 PUT，删除使用 DELETE。
- Controller 不编写业务逻辑，不直接调用 Mapper。
- 请求 DTO 使用 Jakarta Validation；响应使用 VO，不直接暴露 Entity。
- 日期时间采用 ISO-8601；金额使用十进制定点类型，不使用浮点数。

## 2. 统一响应

所有业务接口返回 `ApiResponse<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "traceId": "链路标识",
  "timestamp": "2026-08-03T02:00:00Z"
}
```

`code` 为整数。成功码为 200；参数或业务错误使用明确的 4xx 语义；未认证为 401；无权限为 403；未处理系统异常为 500。禁止将堆栈、SQL、密钥或内部路径返回客户端。

## 3. 分页

分页请求统一使用 `pageNo`、`pageSize`，默认值由接口定义；响应至少包含 `records`、`total`、`current`、`size`。必须限制单页最大数量，避免无界查询。

## 4. 认证与权限

- JWT Header：`Authorization: Bearer <token>`。
- Controller 接口必须声明匹配 `sys_menu.permission` 的 `@PreAuthorize`。
- 菜单权限与按钮权限采用 `模块:操作` 命名；现有兼容权限不得无审批改名。
- 业务数据查询还必须遵循 `data-scope-rule.md`，功能权限不能替代数据权限。

## 5. 幂等、审计与错误

- 创建、删除、状态变更等写操作必须评估重复提交并提供业务唯一约束或幂等机制。
- 重要写操作使用操作日志注解，日志参数须先脱敏。
- 异常统一交由 `GlobalExceptionHandler`，Controller 不自行吞掉异常。
- 每次响应携带 TraceId，排障以 TraceId 关联应用日志和审计日志。

## 6. 兼容性

API 的字段删除、改名、语义变化属于破坏性变更，必须发布新版本或提供兼容窗口。新增可选字段保持向后兼容，前端不得依赖未承诺的字段顺序。
