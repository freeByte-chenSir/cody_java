# Jody Web — 实时 AI 对话前端

React SPA 前端，提供可视化的实时 AI 编程助手对话界面。后端为 Spring Boot（`jody-web/`）。

---

## 快速开始

```bash
# 安装依赖
cd web && npm install

# 开发模式 — 启动 Vite HMR（后端需先启动 Spring Boot）
npm run dev
# 打开浏览器 → http://localhost:5173
```

**生产模式：**

```bash
cd web && npm run build    # 输出到 web/dist/
# 后端 Spring Boot 自动托管编译后的前端静态文件
# 打开浏览器 → http://localhost:8000
```

---

## 架构

```
浏览器 (React SPA, port 5173)
  │
  ├── HTTP /api/*  ──→  Spring Boot (port 8000)  ──→  Jody Core
  └── WS /ws/chat/*  ──→  Spring Boot (port 8000)  ──→  AgentRunner
```

- **前端** — React 18 + TypeScript + Vite，通过 Vite proxy 连接后端
- **后端** — Spring Boot 3 + WebFlux，直接调用 Jody Core（无 HTTP 中间层）
- **数据库** — 会话存 `~/.jody/sessions.db`（SQLite）

---

## 前端结构

```
web/src/
├── main.tsx              # 入口
├── App.tsx               # 路由（/ → HomePage, /chat/:id → ChatPage）
├── index.css             # 全局样式（暗色主题）
├── types/index.ts        # TypeScript 接口定义
├── api/client.ts         # HTTP + WebSocket 客户端
├── pages/
│   ├── HomePage.tsx      # 项目列表 + 创建入口
│   └── ChatPage.tsx      # 对话页面
└── components/
    ├── ProjectWizard.tsx  # 目录浏览 + 项目创建表单
    ├── ChatWindow.tsx     # 消息列表 + 输入框（WebSocket 连接）
    ├── MessageBubble.tsx  # 单条消息展示
    └── Sidebar.tsx        # 项目侧边栏
```

### 数据流

1. **项目创建** — `ProjectWizard` 浏览目录 → 选择路径 → `POST /api/projects` → 自动创建 `.jody/` 和关联 session
2. **实时对话** — `ChatWindow` 建立 `WS /ws/chat/{projectId}` → 发送消息 → 接收流式事件（`text_delta` / `tool_call` / `done`）
3. **会话持久化** — 每个项目绑定一个 Jody session，多轮对话自动保持上下文

---

## 后端结构

```
jody-web/src/main/java/com/jody/web/
├── JodyWebApplication.java    # Spring Boot 启动类
├── config/
│   └── AppState.java           # Spring Bean 单例状态管理
├── controller/
│   ├── RunController.java      # POST /run, /run/stream（Agent 执行）
│   ├── SessionController.java  # 会话管理
│   ├── ToolController.java     # 工具管理
│   └── HealthController.java   # 健康检查
├── ws/
│   ├── ChatWebSocketHandler.java  # WebSocket 对话代理
│   └── WebSocketConfig.java       # WebSocket 路由配置
└── filter/
    └── AuthFilter.java         # API Key / Bearer Token 鉴权
```

### 端点一览

| 类别 | 端点 | 说明 |
|------|------|------|
| **项目** | `GET/POST /api/projects`, `GET/PUT/DELETE /api/projects/{id}` | 项目 CRUD |
| **目录** | `GET /api/directories?path=...` | 目录浏览 |
| **对话** | `WS /ws/chat/{project_id}` | 实时 AI 对话 |
| **执行** | `POST /run`, `POST /run/stream` | Agent 单次/流式执行 |
| **工具** | `POST /tool` | 直接调用工具 |
| **会话** | `GET/POST /sessions`, `GET/DELETE /sessions/{id}` | 会话管理 |
| **技能** | `GET /skills`, `GET /skills/{name}` | 技能查询 |
| **Agent** | `POST /agent/spawn`, `GET/DELETE /agent/{id}` | 子 Agent 管理 |
| **审计** | `GET /audit` | 审计日志 |
| **健康** | `GET /health`, `GET /api/health` | 健康检查 |
| **WebSocket** | `WS /ws` | 通用 RPC WebSocket |

### 中间件

按注册顺序（外→内）：
1. **audit** — 记录所有 API 请求（方法、路径、耗时、状态码）
2. **rate_limit** — 基于 IP 的请求限流（可配置）
3. **auth** — Bearer Token / API Key 验证（可选）

公开路径（免认证）：`/health`, `/api/health`, `/docs`, `/openapi.json`, `/redoc`

---

## 测试

```bash
# 前端测试（Vitest + jsdom，33 个）
cd web
npx vitest run
```

| 测试文件 | 覆盖内容 |
|---------|---------|
| `__tests__/api/client.test.ts` | API 客户端 mock 测试 |
| `__tests__/components/*.test.tsx` | React 组件测试 |
| `__tests__/pages/*.test.tsx` | 页面路由测试 |
| `tests/test_projects.py` | 项目 CRUD |
| `tests/test_chat.py` | WebSocket 对话 |
| `tests/test_directories.py` | 目录浏览 |
| `tests/test_run.py` | Agent 执行 |
| `tests/test_sessions.py` | 会话管理 |
| `tests/test_skills.py` | 技能查询 |
| `tests/test_tool.py` | 工具调用 |
| `tests/test_health.py` | 健康检查 |

---

## 技术栈

| 层 | 技术 |
|---|------|
| 前端框架 | React 18 + TypeScript |
| 路由 | React Router 6 |
| 构建 | Vite 6 |
| 样式 | CSS（暗色主题） |
| Markdown | react-markdown + remark-gfm |
| 前端测试 | Vitest + Testing Library |
| 后端框架 | Spring Boot 3 + WebFlux |
| 数据库 | SQLite（WAL 模式） |
| 后端测试 | JUnit 5 + Spring Boot Test |

---

## 开发备注

- Vite dev server（5173）通过 proxy 转发 `/api` 和 `/ws` 到后端（8000）
- 生产构建后，后端 Spring Boot 自动托管 `web/dist/` 静态文件，并提供 SPA fallback
- 后端直接调用 Jody Core，不经过 HTTP SDK — 零网络开销
- 每个项目创建时会自动初始化 `.jody/config.json` 并创建关联的 Jody session
- 流式状态栏 — 显示 Thinking/Running/Generating 状态 + 实时耗时 + Stop 按钮
- WebSocket 断连恢复 — streaming 中 WS 断连时自动重置状态并提示 "Connection lost"
- 空闲超时 — 120s 无事件自动停止 streaming，防止永久卡住
- GFM Markdown — `remark-gfm` 支持表格、删除线、任务列表等 GitHub 风格 Markdown
