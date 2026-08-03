# 数据库实体映射审计记录

## 历史映射问题

`project_info`：

缺少7个字段映射。

`project_stage`：

缺少 `delete_token` 字段。

`project_task`：

缺少6个字段映射。

`project_member`：

缺少 `delete_token` 字段。

## 处理原则

1. 当前Sprint不修复。
2. 保留现有业务代码。
3. 在Project Lifecycle模块专项Sprint中处理。
4. 不影响基础平台建设。
