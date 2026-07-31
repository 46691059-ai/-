# 参与开发

本项目采用受保护的 Git Flow：

- `main`：生产版本；
- `develop`：开发集成及测试版本；
- `feature/*`：功能开发；
- `hotfix/*`：生产紧急修复。

项目生命周期模块使用 `feature/project-module` 开发，经测试后合并到 `develop`；
集成验收通过后再由 `develop` 发布到 `main`。

完整规范、测试门禁、发布和 Hotfix 流程见
[`docs/git-branching-strategy.md`](docs/git-branching-strategy.md)。
