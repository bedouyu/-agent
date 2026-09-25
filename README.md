# 论文格式修改 Agent

这是一个适合边学习边开发的本机单人应用：

- Java 17 + Spring Boot：后端 API
- H2：项目目录中的本地文件数据库
- WPF + .NET 10：Windows 客户端
- JPA：用清晰的 Java 代码完成常规数据库操作

当前版本已经打通四条流程：

- Windows 客户端创建论文项目 → Java API → H2 保存 → 客户端显示。
- 选择项目并上传 `.zip`、`.tex` 或 `.docx` → 后端校验文件 → 原稿保存到本地 → H2 记录文档信息。
- 选择 LaTeX 文档 → TeX Live 编译 → 应用内预览 PDF → 点击 PDF → SyncTeX 定位到 `.tex` 文件和行号。
- 选中 LaTeX 源码片段 → 确认发送范围和最多两次模型调用 → 编辑 Agent 生成建议 → 审查 Agent 复核 → 手动复制建议。

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

## 使用 DeepSeek 建议

1. 将 `DEEPSEEK_API_KEY` 保存为 Windows 用户环境变量；重启 IDEA，再运行后端。密钥不要写入 `application.yml`、Java 源码或 Git。
2. 打开前端的“AI 润色与格式建议”页，确认显示“已读取本机 DeepSeek 密钥”，并选择任务与模型。
3. 切回“PDF 与源码”，选中不超过 2000 个字符的 LaTeX 片段，点击“生成 AI 建议”并确认发送。
4. 对照原文阅读建议、Agent 执行过程和复核结论。程序只展示、复制建议，不自动覆盖原始 `.tex`。复核结论也不能代替人工检查。

确认后，编辑 Agent 将选中片段发送给 DeepSeek；若本地安全检查通过，审查 Agent 还会将该片段及候选建议发送给 DeepSeek 复核。一次操作最多两次模型调用，需要联网并可能产生 API 费用。若引用、标签、环境或数学分隔符发生可疑变化，审查 Agent 不会发起第二次调用，而是提示人工检查。模型状态接口不会返回密钥。`deepseek-flash` 是默认模型，也可选 `deepseek-v4-pro`。

多 Agent 的编排代码在 `backend/src/main/java/com/paperagent/agent/`。当前是“编辑 → 审查”的有序流程，**还没有自动修改、编译、重试的循环**。新增专职 Agent 可实现 `PaperAgent` 接口、注册为 Spring 组件，并用 `@Order` 指定顺序；模型通信经过 `AiModelGateway` 接口，当前由 `DeepSeekService` 实现，以后可替换为 Spring AI 适配器。

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
GET  http://127.0.0.1:18080/api/ai/status
GET  http://127.0.0.1:18080/api/ai/agents
POST http://127.0.0.1:18080/api/ai/projects/{项目ID}/documents/{文档ID}/suggest
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

后端仅监听 `127.0.0.1`。项目、源码、PDF 和数据库保存在本机 `data/` 下；仅手动确认的选中片段及其候选建议可能发送给 DeepSeek。论文正文、模型回复和密钥不写入数据库或日志。

## 推荐阅读

- [架构说明](docs/01-架构说明.md)
- [边做边学路线](docs/02-学习路线.md)
