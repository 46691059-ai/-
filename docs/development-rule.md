# 企业级开发规范

版本：V1.0.0

## 1. 分支与提交

- `main` 保存生产版本，`develop` 保存开发集成版本。
- 功能使用 `feature/*`，紧急修复使用 `hotfix/*`。
- 功能分支通过测试和审查后合并 develop；发布验收后再进入 main 并创建版本标签。
- 禁止提交 `.env`、密钥、数据库备份、运行日志、构建产物和依赖目录。

## 2. 后端

- Java 21；Controller、Service、Mapper、Entity、DTO、VO 职责分离。
- 写操作声明合理事务边界；只读查询避免不必要事务。
- 所有外部输入校验，所有 SQL 参数化；严禁输出密码、摘要、完整 JWT 和身份证号。
- 新接口必须同时设计功能权限、数据权限、审计日志、异常语义和测试。
- 公共能力进入 `common/config/security`，模块私有能力保留在对应 `modules` 中。

## 3. 前端

- Vue 3 Composition API + TypeScript；跨页面状态使用 Pinia。
- 所有 API 通过统一 Axios 实例；401/403/5xx 使用统一处理。
- 路由由后端授权菜单动态生成；按钮使用 `v-permission`，但安全校验以后端为准。
- 页面组件避免包含复杂业务规则，API 类型集中维护。

## 4. 数据库与安全

- 先完成数据库与接口设计，再实现后端、前端和测试。
- 禁止未经评审修改既有数据库结构。
- 敏感配置仅通过环境变量或 secrets 注入，不在源码设置生产默认值。
- 业务查询必须遵循 `data-scope-rule.md`。

## 5. 质量门禁

提交前至少执行：

```text
backend:  mvn clean verify
frontend: npm install && npm run test && npm run type-check && npm run build
```

同时执行核心 Entity 映射、未认证 401、越权 403、异常 JWT、敏感信息和部署配置检查。不得用删除测试、跳过安全检查或降低编译级别的方式通过门禁。

## 6. 冻结后变更

V1.0.0 标签创建后，修复必须通过 hotfix 或新 Sprint，形成明确变更记录并重新执行受影响的回归测试。禁止移动或覆盖冻结标签。
