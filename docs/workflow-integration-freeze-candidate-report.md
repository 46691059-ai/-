# Workflow联调冻结候选版本报告

## 1. 报告信息

| 项目 | 内容 |
|---|---|
| Sprint | Sprint 2-3.7.8 |
| 生成日期 | 2026-08-10（Asia/Shanghai） |
| 项目 | 县域国企数字化运营治理平台 |
| 候选状态 | **VERSION_FREEZE_READY** |
| 联调状态 | **WORKFLOW_RESOURCE_NOT_READY / NO_GO** |

本Sprint只进行版本冻结治理，没有新增业务功能，没有修改V2.4.0—V2.4.9历史Migration，没有启动Outbox Worker，没有进入真实Workflow联调，也没有提交任何密钥。

## 2. 版本信息

### 2.1 冻结候选标识

| 标识 | 值 |
|---|---|
| 分支 | `feature/project-module` |
| 候选Git Commit | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` |
| 候选Commit短值 | `abb5fc9` |
| 候选Tree | `719d25d0d1408b2e5f36b3510cae91c30008bc1f` |
| 候选标签 | `v2.4.9-workflow-integration-rc1` |
| 标签指向 | `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8` |
| Maven项目版本 | `1.0.0` |
| Java基线 | Java 21 |
| Migration最高版本 | V2.4.9 |

候选标签只标识非生产Workflow联调候选，不表示生产发布，也不表示真实Workflow资源已经READY。

本报告作为治理文档在候选Commit之后生成；候选代码、配置、Migration和既有文档的唯一可复现基线以上述Commit和标签为准。本报告后续提交不得改变候选标签指向。

### 2.2 构建产物

| 项目 | 值 |
|---|---|
| 产物 | `backend/target/enterprise-platform.jar` |
| 大小 | 53,455,600 bytes |
| SHA-256 | `8fb7a1ea835ce28c4315085872479ed531fcb36428fe7dfed3208bd7415feee4` |

`target`目录被Git忽略，JAR不提交到仓库。摘要用于核对本次候选构建，不替代制品仓库签名、SBOM或CI可复现构建。

## 3. 代码状态

### 3.1 工作区治理结果

| 检查项 | 结果 |
|---|---|
| 候选提交前工作区 | 21条已跟踪变更、277个实际未跟踪文件 |
| 候选提交文件数 | 299 |
| 候选提交统计 | 33,413 insertions / 61 deletions |
| 提交后工作区 | 干净 |
| 临时或二进制未跟踪文件 | 0 |
| 大于1 MiB的未跟踪文件 | 0 |
| 高置信密钥扫描 | 298个候选文本文件，命中0 |
| `git diff --cached --check` | 通过 |

候选提交内容为此前Project、Investment、Workflow可靠性、Migration治理、测试及文档Sprint的累计工作区成果。本Sprint没有新增业务能力，只完成分类审计、配置模板补全、机械空白规范化、构建验证和版本提交。

### 3.2 候选提交分类

| 分类 | 文件数 |
|---|---:|
| 应用代码 | 176 |
| 测试代码 | 27 |
| 数据库资产 | 37 |
| 配置 | 3 |
| 文档及README | 56 |
| 合计 | 299 |

### 3.3 数据库资产冻结

| 检查项 | 结果 |
|---|---|
| 正式Migration文件数 | 15 |
| 版本范围 | V2.1.0—V2.4.9 |
| 重复版本 | 0 |
| V2.4.0—V2.4.9权威资产 | 10/10 SHA-256与Inventory一致 |
| 历史聚合SQL | 已迁入`database/mysql/deprecated` |
| 历史聚合SQL正文 | 与原HEAD正文逐行一致，仅新增两行弃用说明 |

V2.4.0—V2.4.9未被本Sprint修改。候选提交固化其既有内容，后续变更必须新增更高版本Migration。

## 4. 构建结果

### 4.1 构建环境

| 组件 | 版本 |
|---|---|
| JDK | Eclipse Temurin 21.0.12+8 LTS |
| Maven | 3.9.9 |
| Spring Boot | 3.5.9 |
| 操作系统 | Windows 10 amd64 |
| 平台编码 | UTF-8 |

构建使用工作区外的便携运行时，未安装系统软件，也未修改项目Maven配置。

### 4.2 编译与测试

执行：

```text
mvn test
mvn -DskipTests package
```

| 检查项 | 结果 |
|---|---|
| Maven编译 | PASS |
| Spring上下文测试 | PASS |
| 测试套件 | 52 |
| 测试用例 | 187 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| JAR打包 | PASS |

测试日志中的非阻断提醒：

1. H2测试上下文没有创建完整业务表，`DatabaseMappingChecker`对35张表输出`TABLE_NOT_FOUND`警告；测试仍通过，但不能替代真实MySQL V2.4.9映射验收。
2. Mockito/Byte Buddy提示未来JDK将限制动态Agent加载；当前Java 21测试通过，后续需在测试构建中显式配置Mockito Agent。

本次没有连接真实Workflow、Redis或目标MySQL，因此构建通过不代表外部资源准入通过。

## 5. 配置状态

### 5.1 配置冻结结果

| 文件 | SHA-256 |
|---|---|
| `backend/src/main/resources/application.yml` | `9bc94bfba8ad81013618fdc10f58b81dec3b615b20d76a391a9956e9885b6356` |
| `deploy/docker-compose.yml` | `aea7cc575a1170039e8c433468c6306e3197b4ce8282a78b0375a920112345a0` |
| `deploy/.env.example` | `72ea16f7c7c11a79bcda3989e4c323660c4189f7af487724f0348755d0ba7e05` |
| `deploy/.env.production.example` | `bdc9c8834a39d43098de4cf8408e35e0b3523608c5df8ff4dd597b1f73ed200d` |
| `backend/pom.xml` | `d6581de9e67d1acf6e34f2291f337c21973f47048fe42231ce48e496de0ad776` |

### 5.2 Workflow配置治理

已确认并固化：

- `application.yml`通过外部变量读取Workflow地址、服务Token、回调Token、流程定义、HMAC和可靠性配置；
- Outbox Worker默认值保持`false`；
- 部署模板声明Workflow地址、定义键、显式版本、当前Key ID和Worker开关；
- Docker Compose使用Docker secrets向Spring Config Tree注入Service Token、Callback Token和HMAC Secret；
- `deploy/secrets/`由`.gitignore`排除；
- 环境变量模板只包含非敏感值或明确占位符；
- 配置敏感字面量检查命中0；
- Docker Compose YAML通过SnakeYAML语法解析。

未执行`docker compose config`和容器启动验证，因为当前环境没有Docker CLI。该项仍属于真实联调部署前检查项。

### 5.3 密钥管理结论

候选提交不包含Workflow Token、Callback Token、HMAC Secret、数据库密码、Redis密码或JWT Secret明文。真实值必须在非生产运行环境通过密钥管理系统或Docker secrets交付，不得写入Git、普通`.env`、镜像、报告或日志。

## 6. 联调准入结论

### 6.1 版本冻结结论

| 门禁 | 结果 |
|---|---|
| Git候选Commit | PASS |
| 候选标签 | PASS |
| 工作区治理 | PASS |
| Java 21/Maven构建 | PASS |
| 187项后端测试 | PASS |
| JAR产物生成 | PASS |
| Migration资产完整性 | PASS |
| 配置模板和密钥外部化 | PASS |
| 高置信密钥扫描 | PASS |
| 版本冻结 | **READY** |

已形成唯一可复现的Workflow联调候选版本：

`v2.4.9-workflow-integration-rc1` → `abb5fc9eb3a075b723eb5e98d9d9d7932429b8c8`

### 6.2 真实联调结论

真实联调资源仍未交付：Investment、Workflow、Redis、目标MySQL、流程定义、安全凭证及测试账号均未通过当前环境准入。

因此总体状态为：

**VERSION_FREEZE_READY / WORKFLOW_RESOURCE_NOT_READY / NO_GO**

在资源READY并重新执行Sprint 2-3.7.7启动门禁前，不启动Outbox Worker，不发起真实Workflow流程。

## 7. 后续要求

1. 不得修改候选标签指向或重写候选Commit历史。
2. 候选缺陷修复必须创建新Commit和`rc2`标签；不得修改V2.4.0—V2.4.9。
3. CI或制品平台应从候选Commit重新构建，生成独立制品摘要、SBOM和依赖漏洞报告。
4. 资源交付后重新核验目标MySQL Flyway history、严格`validate`和V2.4.9结构指纹。
5. 只有服务、Workflow定义、安全、账号和数据库全部READY，才允许进入Sprint 2-3.7真实联调。
