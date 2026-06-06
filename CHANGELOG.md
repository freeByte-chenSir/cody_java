# Changelog

All notable changes to this project will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/).

---

## [0.1.0] - 2026-06-06

### Added

- **Core Engine** (`cody-core`) — Agent 执行引擎、工具注册系统、配置管理、System Prompt 构建
- **30 个内置工具** — 12 大分类：File I/O (4)、Search (4)、Command (1)、Skills (2)、SubAgent (4)、MCP (2)、Web (2)、LSP (4)、FileHistory (3)、Todo (2)、User (1)、Memory (1)
- **AgentRunner** — run / stream / runSync 三种执行模式，最大 50 步循环
- **ToolRegistry** — 声明式工具注册表，按类别组织，支持子 Agent 工具集
- **Config** — 三层配置加载：默认值 → 全局配置 → 项目配置 → 环境变量覆盖
- **SessionStore** — SQLite 会话持久化，完整 CRUD + 消息存储
- **SkillManager** — Agent Skills 开放标准解析，YAML frontmatter 支持
- **SubAgentManager** — 子 Agent 编排（code/research/test/generic），并发执行
- **CircuitBreaker** — Token/成本/步数上限 + 死循环检测熔断
- **PermissionManager** — 工具级权限（allow/deny/confirm）+ 人工确认流程
- **AuditLogger** — SQLite 审计日志，记录每次工具调用
- **RateLimiter** — 滑动窗口限流
- **FileHistory** — 文件修改 undo/redo，每文件最多 50 条记录
- **ContextManager** — 工具输出修剪 + 消息压缩 + 智能文件分块
- **ProjectMemory** — 跨任务记忆（conventions/patterns/issues/decisions）
- **MCPClient** — stdio + HTTP 双传输 MCP 客户端
- **LSPClient** — 多语言 LSP 支持（Python pyright、TypeScript tsserver、Go gopls）
- **InteractionHandler** — 人机交互代理（question/confirm/feedback）
- **UserInputQueue** — BlockingQueue 用户主动输入注入
- **SystemPrompt** — 分层 system prompt 构建（persona + CODY.md + skills + memory）

- **Java SDK** (`cody-sdk`) — Builder 模式客户端
  - `Cody` Builder — 链式配置：model、apiKey、baseUrl、enableThinking、autoApprove、workdir
  - `CodyClient` — run / runWithSession / stream / streamWithEvents 方法
  - `StreamChunk` — 流式事件类型（TextDelta、ToolCall、ToolResult、Done、Error、Cancelled）
  - `SdkConfig` — SDK 配置，覆盖 core Config

- **CLI** (`cody-cli`) — Picocli 命令行
  - `run` 命令 — 执行单次 AI 任务
  - `chat` 命令 — 交互式对话
  - `config` 命令 — 配置管理
  - `ConsoleRenderer` — 流式事件终端渲染

- **Web Backend** (`cody-web`) — Spring Boot 3 + WebFlux
  - `RunController` — POST /run, /run/stream (SSE)
  - `SessionController` — CRUD /sessions
  - `ToolController` — GET /tool, POST /tool/{name}
  - `HealthController` — GET /health
  - `ChatWebSocketHandler` — WebSocket /ws 双向通信
  - `AuthFilter` — X-API-Key / Bearer Token 鉴权
  - `AppState` — Spring Bean 单例状态管理

- **Web Frontend** (`web/`) — React + TypeScript + Vite SPA
  - 6 个页面：HomePage, ChatPage, ProjectDetailPage, TaskChatPage, SkillsPage, SettingsPage
  - WebSocket 自动重连（指数退避），实时流式消息渲染
  - Vite 开发代理到 Spring Boot 后端（端口 8000）
  - 33 个前端测试（Vitest + Testing Library）

- **项目文档** — README.md, CLAUDE.md, CONTRIBUTING.md, CHANGELOG.md

### Technical Stack

- Java 17
- LangChain4j (AI Agent 框架)
- Spring Boot 3 + WebFlux (Web Backend)
- Picocli (CLI)
- SQLite via JDBC (数据持久化)
- OkHttp (MCP HTTP 传输)
- Jackson (JSON)
- React 18 + TypeScript + Vite (Web Frontend)
- Maven 多模块构建
