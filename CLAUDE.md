# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Cody Java 项目指南

## 项目概述

Cody Java 是 **AI Coding Agent 框架** 的 Java 17 实现，提供构建 AI 编程 Agent 所需的完整基础设施。

- **Core Engine** (`cody-core/`) — 框架核心，所有功能逻辑，不依赖任何 CLI / Web 框架
- **Java SDK** (`cody-sdk/`) — 框架的主要接入方式，直接包装 core（in-process，无 HTTP）
- **CLI** (`cody-cli/`) — 命令行参考实现（Picocli）
- **Web Backend** (`cody-web/`) — Spring Boot 应用（端口 8000），提供 HTTP API + WebSocket
- **Web Frontend** (`web/`) — React + TypeScript + Vite SPA，6 个页面，Vite 代理到后端

## 架构要点

```
cody-sdk/ (Java SDK)  →  cody-core/
cody-cli/             →  cody-sdk/       →  cody-core/
cody-web/             →  cody-core/
                               ↓
                     LangChain4j, SQLite, OkHttp
```

- `cody-core/` **不允许**导入 `cody-cli/`、`cody-sdk/` 或 `cody-web/`，禁止反向依赖
- CLI 通过 SDK（`CodyClient`）访问 core，不直接导入 `AgentRunner`/`SessionStore`
- SDK、CLI、Web Backend 都是 core 的平行消费者
- 新功能先在 `cody-core/` 实现，再通过 SDK 暴露，最后在参考实现中使用
- 工具注册是声明式的：在 `ToolRegistry.java` 的 static 块中添加到对应的 `*_TOOLS` 列表即可
- Web Backend 使用 Spring Bean 注入依赖；状态管理见 `AppState.java`

### 开发流程

新功能的完整路径：`cody-core/` 实现 → SDK 暴露 → Web Backend 路由 → CLI 命令 → 更新文档

### 关键入口文件

- `cody-core/.../runner/AgentRunner.java` — 框架中枢：Agent 创建、工具注册、run/stream 执行、熔断检查、记忆加载
- `cody-core/.../tool/ToolRegistry.java` — 声明式工具注册表（`*_TOOLS` 列表），添加新工具只需追加到列表
- `cody-core/.../deps/CodyDeps.java` — 依赖注入容器，ToolContext 提供工具执行上下文
- `cody-core/.../prompt/SystemPrompt.java` — System prompt 构建
- `cody-sdk/.../Cody.java` — SDK 入口：Builder 模式创建 CodyClient
- `cody-sdk/.../CodyClient.java` — SDK 客户端：直接包装 core 的 run/stream 方法

## 开发命令

```bash
# ── 后端 ──
mvn compile                   # 编译所有模块
mvn test                      # 运行所有测试
mvn test -pl cody-core        # 运行单个模块测试
mvn test -Dtest="GrepToolTest" # 运行匹配名称的测试
mvn package -DskipTests       # 打包
mvn install -DskipTests       # 安装到本地仓库
mvn spring-boot:run -pl cody-web  # 启动 Web 后端

# ── 前端 ──
cd web
npm install                   # 安装依赖
npm run dev                   # 开发模式（Vite HMR，端口 5173）
npm run build                 # 生产构建
npm run test                  # 前端测试（Vitest）
```

## 代码规范

- **Java 17+**，利用 Record、Sealed Classes、Pattern Matching 等特性
- **编码 UTF-8**，4 空格缩进
- **命名** — 类 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`
- **公开 API** 必须有 Javadoc，内部方法按需
- **模块结构** — 包按功能组织（`tool.file`, `tool.search`, `security`, `session` 等）
- 工具异常用 `ToolInvalidParams` / `ToolPathDenied` / `ToolPermissionDenied`，不用通用 `RuntimeException`
- 不依赖真实 API Key — 用 Mock 模拟 LLM

### 测试要求

| 变更类型 | 最低测试数 |
|----------|-----------|
| 新工具 | 3 个：正常路径、边界情况、错误处理 |
| 新 API 端点 | 2 个：正常响应、错误响应 |
| 新 CLI 命令 | 1 个：基本调用 |
| Bug 修复 | 必须附带回归测试 |

### Git 规范

- 分支：`main`（稳定）、`feature/xxx`、`fix/xxx`
- 提交信息格式：`<动词> <做了什么>`（英语，祈使句，不加句号）
  - 例：`Add grep tool with regex search and include filter`
  - 例：`Fix path traversal security check using resolve()`

## 文档更新

**开发完新功能后，必须同步更新项目中的所有相关 `.md` 文档**，保持文档与代码同步。

> **原则**：文档是代码的一部分，不是事后补充。代码合并前，文档必须先更新。

## 版本管理

Java 版本号在各模块 `pom.xml` 中的 `<version>` 统一管理。

## 已知注意事项

1. **工具注册** — 在 `ToolRegistry.java` 的 static 块中声明式注册，添加新工具只需追加到对应 `*_TOOLS` 列表
2. **状态缓存** — `AppState.java` 管理所有 Spring Bean：Config、SessionStore、CodyClient、SkillManager
3. **流式事件** — `StreamEvent` 是抽象类 + 静态内部类的 discriminated union 模式，POISON_PILL 标记流结束
4. **熔断器** — `CircuitBreaker` 使用静态方法，每个 run 创建独立的 `CircuitState`

## 文档

详细文档在项目根目录 `*.md` 文件中（README、CHANGELOG、CONTRIBUTING）。
