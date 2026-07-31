# Git 分支与发布管理规范

## 1. 分支模型

| 分支 | 用途 | 来源 | 合并目标 | 部署环境 |
|---|---|---|---|---|
| `main` | 已验收、可回滚的生产版本 | `develop` / `hotfix/*` | — | 生产环境 |
| `develop` | 日常集成和系统测试版本 | `main` | `main` | 测试环境 |
| `feature/*` | 独立功能开发 | `develop` | `develop` | 临时验证环境 |
| `hotfix/*` | 生产紧急修复 | `main` | `main`、`develop` | 预发布、生产 |

禁止直接向 `main`、`develop` 推送代码，必须通过 Pull Request/Merge Request 合并。

## 2. 项目模块开发流程

```text
develop
   │
   └── feature/project-module
              │
              ├── 单元测试、接口测试、前端构建
              │
              └── PR/MR → develop
                           │
                           ├── 测试环境部署
                           ├── 集成测试、回归测试、安全检查
                           │
                           └── 发布 PR/MR → main
                                             │
                                             ├── 创建版本标签
                                             └── 生产部署
```

生产环境只能部署 `main` 上带版本标签的提交，不能直接部署 `develop` 或功能分支。

## 3. 功能开发操作

```bash
git switch develop
git pull --ff-only origin develop
git switch -c feature/project-module

# 开发并进行小步提交
git add <本次修改文件>
git commit -m "feat(project): 完成项目生命周期模块"

# 合并前同步 develop，优先使用 rebase 保持提交线性
git fetch origin
git rebase origin/develop

# 执行验证
mvn -pl backend test
npm --prefix frontend test
npm --prefix frontend run build

git push -u origin feature/project-module
```

创建 `feature/project-module → develop` 的 PR/MR。至少一名非作者完成代码审查，
所有自动化检查通过后方可合并。

## 4. 测试与发布

功能合并到 `develop` 后：

1. 自动构建不可变版本镜像；
2. 部署到测试环境；
3. 执行数据库迁移验证、接口测试、回归测试和安全扫描；
4. 测试负责人确认验收结果；
5. 创建 `develop → main` 发布 PR/MR；
6. 合并后创建带注释的版本标签；
7. 使用该标签对应的镜像部署生产环境。

```bash
git switch main
git pull --ff-only origin main

# main 的合并应在代码托管平台完成
git tag -a v1.0.0 -m "项目生命周期模块生产发布 v1.0.0"
git push origin v1.0.0
```

生产镜像应使用版本标签或镜像摘要，例如：

```text
enterprise-platform-backend:1.0.0
enterprise-platform-frontend:1.0.0
```

## 5. Hotfix 流程

```bash
git switch main
git pull --ff-only origin main
git switch -c hotfix/security-auth-bypass

# 修复、测试、提交
git push -u origin hotfix/security-auth-bypass
```

紧急修复需执行：

1. `hotfix/* → main` PR/MR；
2. 通过最小必要回归测试和安全复核；
3. 合并 `main`、创建补丁版本标签并部署；
4. 将同一修复同步合并到 `develop`，防止后续版本回归。

## 6. 分支保护规则

### `main`

- 禁止直接推送和强制推送；
- 至少 2 人审批，其中至少 1 名技术负责人；
- 必须通过后端测试、前端测试、构建、依赖扫描、镜像扫描；
- 必须解决全部代码审查意见；
- 仅允许 `develop` 和 `hotfix/*` 合并；
- 删除源分支不影响版本标签。

### `develop`

- 禁止直接推送和强制推送；
- 至少 1 人审批；
- 必须通过自动化测试和构建；
- 仅允许 `feature/*`、`hotfix/*` 及必要的修复分支合并。

## 7. 提交信息

采用 Conventional Commits：

```text
feat(project): 增加项目阶段流转
fix(security): 修复项目数据范围越权
refactor(project): 拆分任务命令服务
test(project): 增加阶段状态机测试
docs(deploy): 补充生产部署手册
chore(build): 调整 Docker 构建参数
```

一次提交只处理一个明确问题，禁止提交密钥、生产配置、备份文件、构建产物或日志。

## 8. 数据库变更

- 数据库脚本只能新增版本，禁止修改已经发布的迁移文件；
- 功能分支提交新的迁移脚本时，必须同时提交回滚或恢复说明；
- 合并 `develop` 前验证全新建库和已有版本升级；
- 生产执行迁移前必须完成备份并记录变更单；
- 数据库迁移失败时停止应用发布，不允许跳过错误继续上线。
