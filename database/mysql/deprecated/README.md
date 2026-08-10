# Deprecated SQL

本目录只保存历史数据库快照，Docker初始化和生产迁移均不得执行。

- `V1.0.0__enterprise_platform_v1.sql`：历史聚合兼容结构。其Project表与
  `../05_project.sql` 已发生结构漂移，自Sprint 2-0.2.2起停止作为结构来源。

历史文件只用于差异审计和旧环境识别。禁止继续修改其结构内容；任何生产变更必须新增
版本迁移脚本。
