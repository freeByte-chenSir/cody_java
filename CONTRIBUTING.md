# Cody Java 开发规范

> Open-source AI Coding Agent Framework — Java Implementation

## 核心原则

1. **框架核心为先** — Core 是框架的核心引擎，CLI、SDK 和 Web Backend 都是接入层。所有功能先在 core/ 实现，再由各层暴露给用户。
2. **测试必须有** — 没有测试的代码不合并。不只是"能跑"，要验证行为正确。
3. **准确度 > 性能** — 工具的结果准确性是底线。宁可慢一点也不能出错。
4. **简单直接** — 不过度设计，不提前抽象。三行重复代码好过一个过早的抽象。

---

## 架构规范

### 代码分层

```
cody-java/
├── cody-core/       # 框架核心引擎（不依赖任何接入层）
├── cody-sdk/        # Java SDK（Builder 模式、事件流）
├── cody-cli/        # CLI 命令行界面（Picocli）
├── cody-web/        # Web 后端（Spring Boot）
└── web/             # Web 前端（React + TypeScript + Vite）
```

**关键约束：**
- `cody-core/` 内的代码 **不允许** 导入 `cody-cli/`、`cody-sdk/` 或 `cody-web/`
- 所有接入层都通过 `cody-core/` 提供的接口工作
- `cody-sdk/` 是一等公民模块，直接包装 core，提供 Builder 模式、事件流等高级 API
- 新功能应该加在 `cody-core/`，然后在各接入层暴露

### 依赖方向

```
cody-cli/ ────→
cody-web/ ────→  cody-core/*
cody-sdk/   ──→  cody-core/*
```

禁止反向依赖。禁止 `cody-core/` 依赖任何 CLI（Picocli）或 Web（Spring Boot）的库。

---

## 测试规范

### 必须测试

| 变更类型 | 测试要求 |
|----------|----------|
| 新工具 | 至少 3 个测试：正常路径、边界情况、错误处理 |
| 新 API 端点 | 至少 2 个测试：正常响应、错误响应 |
| 新 CLI 命令 | 至少 1 个测试：基本调用 |
| Bug 修复 | 必须附带回归测试（先写测试复现 bug，再修） |
| 核心逻辑变更 | 覆盖所有受影响路径 |

### 测试工具

```bash
# 运行全部测试
mvn test

# 运行单个模块测试
mvn test -pl cody-core

# 运行匹配名称的测试
mvn test -Dtest="GrepToolTest"
```

### 测试原则

1. **不依赖真实 API Key** — 用 Mock 模拟 LLM 调用
2. **文件操作测试用临时目录** — 不污染真实文件系统
3. **测行为不测实现** — 断言输出结果，不断言内部调用
4. **测试命名** — `test_<功能>_<场景>`，例如 `testGrep_SkipsBinaryFiles`

---

## 代码风格

### 检查

```bash
# 编译
mvn compile

# 运行测试
mvn test
```

### 规则

- **Java 版本** — 17+
- **编码** — UTF-8
- **缩进** — 4 空格
- **命名** — 类 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`
- **公开 API** — 必须有 Javadoc，内部方法按需

### 命名约定

- 类名：`PascalCase`
- 方法/变量：`camelCase`
- 常量：`UPPER_SNAKE_CASE`
- 包名：`lowercase`，按模块组织

---

## Git 规范

### 分支

- `main` — 稳定分支，所有测试必须通过
- `feature/xxx` — 功能分支，从 main 拉取
- `fix/xxx` — 修复分支

### 提交信息

格式：`<动词> <做了什么>`

```
Add grep tool with regex search and include filter
Fix path traversal security check using resolve()
Update FEATURES.md with engine-first roadmap
```

- 用英文
- 首字母大写
- 不加句号
- 动词用原形：Add / Fix / Update / Remove / Refactor

### 提交前检查

```bash
# 必须全部通过才能提交
mvn compile
mvn test
```

---

## 新功能开发流程

1. **先写测试** — 或至少同时写测试。不接受"先实现后面再补测试"
2. **先在 core 实现** — 功能逻辑放在 `cody-core/`
3. **SDK 暴露** — 在 `cody-sdk/` 包装 core 接口（如果需要）
4. **Web Backend 端点** — 在 `cody-web/` 暴露 API（如果需要）
5. **CLI 命令** — 在 `cody-cli/` 提供界面（如果需要）
6. **更新文档** — 同步更新所有相关的 `.md` 文档
7. **运行测试** — 全部通过
8. **编译验证** — 零错误

### 示例：添加新工具

```
1. 在 cody-core/src/.../tool/ 对应子包实现工具类（实现 CodyTool 接口）
2. 在 ToolRegistry.java 的 static 块中注册到对应的 *_TOOLS 列表
3. 如果子 Agent 也要用，加到 SUB_AGENT_TOOLSETS 对应的 type 列表
4. 写 3+ 个测试
5. 更新相关文档
6. mvn test + mvn compile 通过
```

> 不需要改 AgentRunner.java — `getTools()` 会自动包含列表里的所有工具。

---

## 当前状态

| 模块 | 文件数 | 状态 |
|------|--------|------|
| cody-core | ~55 | 30 个工具，12 个子系统 |
| cody-sdk | ~6 | Builder 模式，事件流 |
| cody-cli | ~2 | Picocli 命令 |
| cody-web | ~9 | Spring Boot REST + WebSocket |
| web | ~17 | React + TypeScript SPA（6 页面） |

**当前版本：v0.1.0（Java 版初始版本）**

---

## 已知架构注意事项

1. **工具注册** — 在 `ToolRegistry.java` 的 static 块中声明式注册，添加新工具只需追加到列表
2. **状态管理** — `AppState.java` 管理 Spring Bean 单例，Config 按 workdir 加载
3. **流式事件** — `StreamEvent` 使用抽象类 + 静态内部类的 discriminated union 模式
