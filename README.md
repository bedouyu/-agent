# 论文格式修改 Agent

这是一个适合边学习边开发的本机单人应用：

- Java 17 + Spring Boot：后端 API
- H2：项目目录中的本地文件数据库
- WPF + .NET 10：Windows 客户端
- JPA：用清晰的 Java 代码完成常规数据库操作

当前版本已经打通三条流程：

- Windows 客户端创建论文项目 → Java API → H2 保存 → 客户端显示。
- 选择项目并上传 `.zip`、`.tex` 或 `.docx` → 后端校验文件 → 原稿保存到本地 → H2 记录文档信息。
- 选择 LaTeX 文档 → TeX Live 编译 → 应用内预览 PDF → 点击 PDF → SyncTeX 定位到 `.tex` 文件和行号。

格式修改以 UTF-8 LaTeX 工程为主。推荐把包含 `main.tex`、章节源码、参考文献和图片的完整目录压缩为 ZIP 后导入。后续 Agent 会生成修改建议和新版本，不直接覆盖原稿。DOCX 可作为润色或内容参考文件。

当前编译固定使用 `latexmk + XeLaTeX`，并启用 SyncTeX；PDF 页面由 TeX Live 自带的 Ghostscript 渲染，因此不需要另外安装 PDF 组件。

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
3. 双击 `desktop/PaperAgent.Desktop/bin/Release/net10.0-windows/PaperAgent.Desktop.exe`。
4. 在界面中选择 LaTeX 文档，点击“编译 LaTeX”；编译成功后可在右侧阅读 PDF，单击正文可定位左侧源码。

默认 TeX Live 路径是 `D:\texlive\texlive\2026\bin\windows`。换电脑后如路径不同，启动后端前设置环境变量 `LATEX_BIN_DIR` 即可，不需要改 Java 代码。

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
POST http://127.0.0.1:18080/api/projects/{项目ID}/documents/{文档ID}/compile
GET  http://127.0.0.1:18080/api/projects/{项目ID}/documents/{文档ID}/preview
GET  http://127.0.0.1:18080/api/projects/{项目ID}/documents/{文档ID}/synctex?page=1&x=100&y=100
```

`backend/requests.http` 中准备了可以逐个练习的 HTTP 请求。

## 数据在哪里

- H2 数据库：`data/localdb/`
- LaTeX 原稿：`data/documents/{项目ID}/{文档ID}/source.tex`
- LaTeX 工程原始包：`data/documents/{项目ID}/{文档ID}/project.zip`
- LaTeX 工程工作区：`data/documents/{项目ID}/{文档ID}/workspace/`
- 编译后的 PDF 和 SyncTeX：与工作区的 `main.tex` 放在同一目录
- 应用内 PDF 页面图片：`data/documents/{项目ID}/{文档ID}/preview/`
- Word 参考文件：`data/documents/{项目ID}/{文档ID}/original.docx`
- 编译缓存：`.cache/`

迁移到另一台电脑时，复制整个项目目录即可；目标电脑需要 Java 17、Maven、.NET 10 SDK 和 TeX Live。开发工具安装说明见 [tools/install-dev-tools.ps1](tools/install-dev-tools.ps1)。

当前版本没有接入任何大模型服务，也没有向外部服务器发送论文内容；后端仅监听 `127.0.0.1`，项目、源码、PDF 和数据库都保存在本机 `data/` 下。

## 推荐阅读

- [架构说明](docs/01-架构说明.md)
- [边做边学路线](docs/02-学习路线.md)
