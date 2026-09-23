# 论文格式修改 Agent

这是一个适合边学习边开发的本机单人应用：

- Java 17 + Spring Boot：后端 API
- H2：项目目录中的本地文件数据库
- WPF + .NET 10：Windows 客户端
- JPA：用清晰的 Java 代码完成常规数据库操作

当前版本已经打通两条流程：

- Windows 客户端创建论文项目 → Java API → H2 保存 → 客户端显示。
- 选择项目并上传 `.zip`、`.tex` 或 `.docx` → 后端校验文件 → 原稿保存到本地 → H2 记录文档信息。

格式修改以 UTF-8 LaTeX 工程为主。推荐把包含 `main.tex`、章节源码、参考文献和图片的完整目录压缩为 ZIP 后导入。后续 Agent 会生成修改建议和新版本，不直接覆盖原稿。DOCX 可作为润色或内容参考文件。

## 目录

```text
backend/       Java 后端
desktop/       Windows WPF 客户端
docs/          架构和学习资料
data/          数据库和论文文件
tools/         开发工具安装脚本
build-all.cmd  编译后端、运行测试并编译客户端
```

## 启动本机模式

本模式不需要 Docker、MySQL 或数据库账号密码。

1. 双击 `start-backend-local.cmd`，启动 Java 17 后端。
2. 确认 `http://127.0.0.1:18080/api/health` 返回 `UP`。
3. 双击 `desktop/PaperAgent.Desktop/bin/Debug/net10.0-windows/PaperAgent.Desktop.exe`。

数据库文件会保存在：

```text
data/localdb/
```

## 编译和测试

双击 `build-all.cmd`，或者执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\build-all.ps1
```

构建缓存保存在项目的 `.cache/`，不会占用 C 盘用户目录，也不会提交 Git。

## 常用接口

```text
GET  http://127.0.0.1:18080/api/health
GET  http://127.0.0.1:18080/api/projects
POST http://127.0.0.1:18080/api/projects
GET  http://127.0.0.1:18080/api/projects/{项目ID}/documents
POST http://127.0.0.1:18080/api/projects/{项目ID}/documents
```

`backend/requests.http` 中准备了可以逐个练习的 HTTP 请求。

## 数据在哪里

- H2 数据库：`data/localdb/`
- LaTeX 原稿：`data/documents/{项目ID}/{文档ID}/source.tex`
- LaTeX 工程原始包：`data/documents/{项目ID}/{文档ID}/project.zip`
- LaTeX 工程工作区：`data/documents/{项目ID}/{文档ID}/workspace/`
- Word 参考文件：`data/documents/{项目ID}/{文档ID}/original.docx`
- 编译缓存：`.cache/`

迁移到另一台电脑时，复制整个项目目录即可；目标电脑需要 Java 17、Maven 和 .NET 10 SDK。开发工具安装说明见 [tools/install-dev-tools.ps1](tools/install-dev-tools.ps1)。

## 推荐阅读

- [架构说明](docs/01-架构说明.md)
- [边做边学路线](docs/02-学习路线.md)
