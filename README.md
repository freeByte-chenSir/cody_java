# Cody Java

**开源 AI Coding Agent 框架** — Java 17 实现，构建、定制和部署你自己的 AI 编程 Agent。

Cody 提供构建 AI 编程 Agent 所需的完整基础设施：**30 个工具、Agent Skills 开放标准、MCP/LSP 集成、子 Agent 编排、熔断器、跨任务记忆、会话管理和安全体系**。你可以用 SDK 将它嵌入任何 Java 应用，也可以直接用 CLI/Web 开箱即用。

---

## 为什么选择 Cody？

| 痛点 | Cody 怎么解决 |
|------|--------------|
| 想自建 AI 编码工具，但从零造轮子太重 | 30 个工具 + 熔断器 + 安全体系 + Sessions 全现成，专注你的业务逻辑 |
| Claude Code / Cursor 不够灵活，想定制 Agent 行为 | Skills 系统 + 权限控制 + 多模型切换，完全可控 |
| 绑定单一模型厂商，切换成本高 | 多模型支持（Claude、GPT、Gemini、DeepSeek 等） |
| 商业产品无法审计、无法私有部署 | 开源 MIT，代码在你手里，可审计、可定制、可离线部署 |

---

## 快速开始

### 方式一：SDK 嵌入（推荐）

```java
// 引入依赖：com.cody:cody-sdk
import com.cody.sdk.Cody;
import com.cody.sdk.CodyClient;

CodyClient client = Cody.builder()
    .model("claude-sonnet-4-0")
    .apiKey("sk-ant-...")
    .workdir(Path.of("/project"))
    .build();

// 让 AI 执行编码任务
AgentRunner.RunResult result = client.run("创建一个 Spring Boot hello world 应用");
System.out.println(result.getOutput());
```

SDK 直接调用核心引擎（in-process），无需启动任何服务。

### 方式二：CLI 开箱即用

```bash
# 从源码构建
mvn package -pl cody-cli -DskipTests

# 配置模型
java -jar cody-cli.jar config setup

# 执行任务
java -jar cody-cli.jar run "创建一个 Spring Boot hello world 应用"

# 交互对话
java -jar cody-cli.jar chat
```

### 方式三：Web 界面

```bash
# 终端 1: 启动后端
mvn spring-boot:run -pl cody-web              # Spring Boot → http://localhost:8000

# 终端 2: 启动前端
cd web
npm install
npm run dev                                   # Vite → http://localhost:5173
```

浏览器打开 `http://localhost:5173`，前端自动代理 API 请求到后端 8000 端口。

---

## 框架能力一览

### 30 个内置工具

| 分类 | 工具 |
|------|------|
| **文件 I/O** | `read_file`, `write_file`, `edit_file`, `list_directory` |
| **搜索** | `grep`, `glob`, `search_files`, `patch` |
| **Shell** | `exec_command` |
| **子代理** | `spawn_agent`, `get_agent_status`, `kill_agent`, `resume_agent` |
| **MCP** | `mcp_call`, `mcp_list_tools` |
| **Web** | `webfetch`, `websearch` |
| **LSP** | `lsp_diagnostics`, `lsp_definition`, `lsp_references`, `lsp_hover` |
| **文件历史** | `undo_file`, `redo_file`, `list_file_changes` |
| **任务管理** | `todo_write`, `todo_read` |
| **用户交互** | `question` |
| **记忆** | `save_memory` |
| **技能** | `list_skills`, `read_skill` |

### Agent Skills 开放标准

兼容 [Agent Skills](https://agentskills.io/) 开放标准（Claude Code、Cursor、GitHub Copilot 等 26+ 平台采用）。你的 Skills 可以跨平台复用。

```markdown
---
name: git
description: Git 版本控制操作。处理 git 仓库时使用。
metadata:
  author: cody
  version: "1.0"
---
# Git 操作
AI 代理的使用说明...
```

**自定义技能：** 在 `.cody/skills/` 或 `~/.cody/skills/` 下创建 SKILL.md，AI 自动发现并按需加载。

**两层优先级：** `.cody/skills/`（项目）> `~/.cody/skills/`（用户）

### 集成能力

- **MCP 集成** — 通过 stdio JSON-RPC 或 HTTP 连接外部 MCP 服务器（GitHub、数据库等）
- **LSP 代码智能** — Python (pyright)、TypeScript (tsserver)、Go (gopls)
- **子代理系统** — 孵化专业代理（code/research/test），并发执行
- **上下文管理** — 接近 token 限制时自动压缩对话，智能文件分块
- **熔断器** — Token/成本上限 + 死循环检测，自动终止失控 Agent
- **跨任务记忆** — AI 自动积累项目经验，注入后续会话
- **人工交互** — AI 主动提问 + 用户随时输入，双向互动

### 安全体系

- 工具级权限控制（allow/deny/confirm）
- 路径遍历保护 + 危险命令检测
- 审计日志（SQLite 持久化）
- 速率限制（滑动窗口）
- 文件修改 undo/redo

---

## 三种使用方式

Cody 的核心是 AI 编程引擎（`cody-core/`），以下三种方式共享同一个引擎：

| 方式 | 适用场景 |
|------|---------|
| **SDK** | 嵌入到你的应用/平台/工具链 |
| **CLI** | 终端中快速执行任务 |
| **Web** | 浏览器界面 + HTTP API |

---

## 项目结构

```
cody-java/
├── cody-core/       # 框架核心引擎（30 工具 + 12 子系统）
├── cody-sdk/        # Java SDK（Builder, Client, Events, Types）
├── cody-cli/        # CLI 命令行（Picocli）
├── cody-web/        # Web 后端（Spring Boot 3 + WebFlux）
└── web/             # Web 前端（React + TypeScript + Vite）
```

---

## 配置

```bash
# 环境变量
export CODY_MODEL='claude-sonnet-4-0'
export CODY_MODEL_API_KEY='sk-ant-...'

# 使用 OpenAI 兼容 API
export CODY_MODEL='gpt-4'
export CODY_MODEL_BASE_URL='https://api.openai.com/v1/'
export CODY_MODEL_API_KEY='sk-...'
```

---

## 开发

```bash
# ── 后端 ──
mvn compile                   # 编译所有模块
mvn test                      # 运行测试
mvn package -DskipTests       # 打包

# ── 前端 ──
cd web
npm install                   # 安装依赖
npm run dev                   # 开发模式（Vite HMR）
npm run build                 # 生产构建
npm run test                  # 前端测试（Vitest）
```

---

## 技术栈

| 层 | 技术 |
|----|------|
| AI Agent 框架 | LangChain4j |
| LLM 调用 | langchain4j-anthropic |
| Web 后端 | Spring Boot 3 + WebFlux |
| CLI | Picocli |
| 数据库 | SQLite via JDBC |
| JSON | Jackson |
| 异步 | CompletableFuture + ExecutorService / Reactor Flux |
| 构建 | Maven（后端）+ Vite（前端） |
| 前端 | React 18 + TypeScript + Vite |
| HTTP 客户端 | OkHttp (MCP) + java.net.http.HttpClient (WebFetch) |

---

## 许可证

MIT License

## 致谢

基于以下优秀项目构建：
- [LangChain4j](https://docs.langchain4j.dev/)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Picocli](https://picocli.info/)
- [Anthropic](https://www.anthropic.com/)

---

**版本:** 0.1.0
