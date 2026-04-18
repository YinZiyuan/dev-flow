# DevFlow — 产品设计文档

**日期：** 2026-04-17  
**状态：** 待实施  
**目标用户：** 个人开发者 / 独立创业者

---

## 1. 产品定位

DevFlow 是一个 **AI 驱动的全流程软件研发工作台**。用户输入一个需求，平台通过 4 个专职 AI Agent 自动推进需求分析 → 实施规划 → 代码生成 → 测试验证全流程（运维部署为 Phase 2），最终输出可运行的代码。全程在浏览器内完成，无需打开任何外部编辑器或终端。

**核心价值主张：** 需求进去，代码出来，过程透明可控，人只在关键节点介入。

**参考来源：** 基于 Multica（AI 原生任务管理平台）重新设计，剥离多人协作复杂度，以 Agent Pipeline 为核心重构产品体验。

---

## 2. 目标用户

- 个人开发者、独立创业者（solo developer）
- 熟悉编程，但希望借助 AI 大幅提升研发效率
- 不需要团队协作功能，追求轻量、快速上手
- **不排斥未来扩展**：数据模型预留多用户支持，但 Phase 1 不实现

---

## 3. 核心概念

### 3.1 实体层级

```
User（用户）
└── Project（项目）— 一个软件项目，包含技术栈配置和本地代码路径
    └── PipelineRun（流水线运行）— 对应一个需求的完整执行过程
        └── StageRun（阶段运行）— 单个阶段的执行实例（可多次，如编码重试）
            ├── Message（消息）— 对话历史，含用户回答、Agent 输出、选项卡
            └── Artifact（产出物）— 阶段输出文件（PRD / 计划 / 代码 / 测试报告）
                └── CodeFile（代码文件）— 仅编码阶段，每个生成的源文件
```

### 3.2 Pipeline 阶段

| 阶段 | Agent 职责 | 人工介入 | 产出物 |
|------|-----------|---------|--------|
| **需求分析** | 理解需求，澄清歧义，生成 PRD | 回答问题、选方案、审批 PRD | PRD 文档（Markdown） |
| **实施规划** | 读取 PRD，拆解任务，设计架构 | 审批计划 | 实施计划（任务列表 + 架构说明） |
| **代码生成** | 按计划逐文件生成代码，实时流式推送 | 旁观/审阅代码、审批或要求修改 | 源代码文件集合 |
| **测试验证** | 在 Docker 沙箱执行测试，失败自动修复 | 超过重试上限时介入，最终审批 | 测试报告 |
| **运维部署** | _(Phase 2，暂不实现，预留)_ | — | — |

---

## 4. 状态机设计

### 4.1 StageRun 状态枚举

| 状态 | 含义 |
|------|------|
| `PENDING` | 等待前一阶段完成，尚未启动 |
| `RUNNING` | Agent 正在工作，LLM 流式输出中 |
| `WAITING_ANSWER` | Agent 提问，等待用户文字回答 |
| `WAITING_CHOICE` | Agent 提出 2-3 个方案，等待用户点选 |
| `WAITING_APPROVAL` | 产出物已就绪，等待用户批准进入下一阶段 |
| `WAITING_REVISION` | 用户要求修改，Agent 即将重新生成 |
| `COMPLETED` | 用户批准，产出物锁定，触发下一阶段 |
| `FAILED` | 不可恢复错误（API 超时、超过最大重试次数等） |
| `SKIPPED` | 用户主动跳过该阶段 |

### 4.2 PipelineRun 顶层状态

| 状态 | 触发条件 |
|------|---------|
| `RUNNING` | 任一 StageRun 处于 `RUNNING` |
| `WAITING_HUMAN` | 任一 StageRun 处于 `WAITING_*` |
| `COMPLETED` | 最后一个阶段 `COMPLETED` |
| `FAILED` | 任一 StageRun `FAILED` 且无法自动恢复 |

### 4.3 各阶段完整状态转换

**需求分析 / 实施规划（相同模式）：**
```
PENDING
  → [自动] RUNNING（Agent 开始分析）
  → [Agent 提问] WAITING_ANSWER
    → [用户回答] RUNNING（可多轮）
  → [Agent 有方案分歧] WAITING_CHOICE
    → [用户选择] RUNNING
  → [草稿生成完毕] WAITING_APPROVAL
    → [用户批准] COMPLETED → 触发下一阶段
    → [用户要求修改] WAITING_REVISION → RUNNING（附带修改意见重新生成）
```

**代码生成：**
```
PENDING
  → [自动] RUNNING（逐文件生成，WebSocket 实时推送到 Monaco Editor）
  → [遇到歧义] WAITING_ANSWER → [回答] RUNNING
  → [所有文件生成完毕] WAITING_APPROVAL
    → [用户批准] COMPLETED → 触发测试阶段
    → [用户要求修改] WAITING_REVISION → RUNNING（增量修改指定文件）
```

**测试验证（含自动修复循环）：**
```
PENDING
  → [自动] RUNNING（Docker 沙箱执行测试，xterm.js 实时输出）
  → [测试失败，iter < max_retries] 自动创建新 coding StageRun（order_index+1）
    → coding RUNNING（仅修改失败相关文件）→ coding COMPLETED
    → 回到 testing RUNNING
  → [测试全部通过] WAITING_APPROVAL
    → [用户批准] COMPLETED → PipelineRun COMPLETED 🎉
  → [iter >= max_retries] FAILED（用户可手动修改代码后点"重新测试"）
```

### 4.4 关键约束

- 同一时间只有一个 StageRun 处于 `RUNNING` 或 `WAITING_*` 状态
- StageRun 一旦 `COMPLETED`，其 Artifact 不可再修改（只读）
- 测试修复循环产生多个 coding StageRun（`order_index` 递增），历史版本全部保留
- `max_retries` 默认 3，可在 Agent 配置中调整
- 用户可在 `WAITING_APPROVAL` 状态下手动编辑 Artifact 内容后再批准

---

## 5. 数据模型

```sql
-- 用户
user (
  id UUID PK,
  email VARCHAR UNIQUE,
  name VARCHAR,
  created_at TIMESTAMP
)

-- 项目
project (
  id UUID PK,
  user_id UUID FK → user,
  name VARCHAR,
  description TEXT,
  tech_stack VARCHAR,        -- e.g. "Java Spring Boot + React"
  repo_path VARCHAR,         -- 本地代码路径
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

-- 流水线运行（一个需求对应一次）
pipeline_run (
  id UUID PK,
  project_id UUID FK → project,
  requirement TEXT,          -- 原始需求文本
  status ENUM(running, waiting_human, completed, failed),
  current_stage ENUM(requirements, planning, coding, testing),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

-- 阶段运行（同一阶段可多次，如编码重试）
stage_run (
  id UUID PK,
  pipeline_run_id UUID FK → pipeline_run,
  stage_type ENUM(requirements, planning, coding, testing),
  status ENUM(pending, running, waiting_answer, waiting_choice,
              waiting_approval, waiting_revision, completed, failed, skipped),
  order_index INT,           -- 同阶段多次时递增
  started_at TIMESTAMP,
  completed_at TIMESTAMP
)

-- 消息（对话历史）
message (
  id UUID PK,
  stage_run_id UUID FK → stage_run,
  role ENUM(user, assistant, system),
  content TEXT,
  type ENUM(text, question, choice_request, choice_response),
  options JSONB,             -- [{id, label, description}]，choice_request 时使用
  selected_option VARCHAR,   -- 用户选择的 option id
  created_at TIMESTAMP
)

-- 产出物
artifact (
  id UUID PK,
  stage_run_id UUID FK → stage_run,
  type ENUM(prd, plan, code, test_result),
  title VARCHAR,
  content TEXT,              -- Markdown 或 JSON
  approved_at TIMESTAMP,     -- NULL 表示未审批
  created_at TIMESTAMP
)

-- 代码文件（仅编码阶段）
code_file (
  id UUID PK,
  artifact_id UUID FK → artifact,
  path VARCHAR,              -- 相对路径，e.g. src/main/java/AuthController.java
  content TEXT,
  language VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

-- Agent 配置（每个阶段一条）
agent_config (
  id UUID PK,
  stage_type ENUM(requirements, planning, coding, testing),
  name VARCHAR,
  system_prompt TEXT,
  model VARCHAR,             -- e.g. claude-opus-4-7
  max_tokens INT,
  max_retries INT,           -- 仅测试阶段有意义
  updated_at TIMESTAMP
)
```

---

## 6. 技术架构

### 6.1 技术选型

| 层 | 技术 | 说明 |
|----|------|------|
| 后端框架 | Spring Boot 3 | Java 生态，成熟稳定 |
| 实时通信 | Spring WebSocket + STOMP | Agent 流式输出推送到前端 |
| 认证 | Spring Security + JWT | 无状态认证 |
| 数据库 ORM | Spring Data JPA + Hibernate | 标准持久层 |
| 数据库 | PostgreSQL 17 | 预留 pgvector 支持（Phase 2 RAG） |
| 前端框架 | Next.js 15 App Router | SSR + 客户端交互 |
| 服务器状态 | TanStack Query | API 缓存 + 实时失效 |
| 客户端状态 | Zustand | UI 状态管理 |
| UI 组件 | Tailwind CSS + shadcn/ui | 快速构建，设计统一 |
| 代码编辑器 | Monaco Editor | VS Code 同款，浏览器内编辑 |
| 终端 | xterm.js + 后端 PTY 进程 | WebSocket 透传，测试输出实时显示 |
| AI 模型 | Anthropic Claude API（Streaming） | SSE → WebSocket 转发 |
| 代码沙箱 | Docker 容器（per pipeline_run） | 测试执行隔离，安全可控 |
| 本地开发 | Docker Compose | 一键启动 PostgreSQL + 后端 + 前端 |

### 6.2 分层架构

```
浏览器
  ├── 需求输入 / 对话界面 / Artifact 查看器
  ├── Monaco Editor（代码编辑）
  └── xterm.js（终端）
      ↕ REST API + WebSocket (STOMP)
后端 Spring Boot
  ├── Pipeline Engine（状态机驱动，核心）
  ├── Agent Executor（调用 Claude API，流式转发）
  ├── Artifact Store（产出物管理）
  └── Event Bus（实时推送状态变更）
      ↕ JDBC / Docker SDK
数据层
  ├── PostgreSQL（主数据）
  └── 本地文件系统（代码文件镜像存储）
AI 层
  ├── Anthropic Claude API
  └── Docker 沙箱（测试执行）
```

---

## 7. 界面设计

### 7.1 页面结构（共 4 个顶级路由）

| 路由 | 页面 | 说明 |
|------|------|------|
| `/` | 项目仪表盘 | 项目卡片列表，显示活跃流水线状态 |
| `/projects/:id` | 项目详情 | 项目信息 + 历史流水线列表 |
| `/runs/:id` | Pipeline 工作台 | 核心工作界面（见下） |
| `/settings` | 设置 | Agent 配置、系统提示词调整 |

### 7.2 Pipeline 工作台布局（三栏）

```
┌─────────────┬───────────────────────────┬──────────────────┐
│  流程进度   │      对话 + 产出物区       │  代码编辑器      │
│  (左侧栏)   │      (中间主区)            │  (右侧，仅编码/  │
│             │                           │   测试阶段显示)  │
│ ● 需求 ✓   │  [消息气泡流]             │  文件树          │
│ ● 规划 ✓   │                           │  ──────────────  │
│ ● 编码 →   │  [底部操作栏]             │  Monaco Editor   │
│ ○ 测试     │  批准 / 要求修改 / 回答    │  ──────────────  │
│             │                           │  xterm.js 终端   │
│ [原始需求]  │                           │  (测试阶段)      │
└─────────────┴───────────────────────────┴──────────────────┘
```

### 7.3 Human-in-the-Loop UI 规则

| 状态 | 前端渲染 |
|------|---------|
| `WAITING_ANSWER` | 底部显示文本输入框 + 发送按钮 |
| `WAITING_CHOICE` | 对话中渲染可点击选项卡（A/B/C），点选后高亮 |
| `WAITING_APPROVAL` | 底部显示"批准进入下一阶段"+ "要求修改"按钮 |
| `RUNNING` | 底部显示流式打字动画，无操作按钮 |
| `FAILED` | 底部显示错误信息 + "重试"或"手动修改后重新测试"按钮 |

---

## 8. 关键实现细节

### 8.1 Agent 流式输出

1. 后端调用 Claude API（SSE Streaming）
2. 逐 token 接收，通过 WebSocket STOMP 推送到前端
3. 前端订阅 `/topic/stage/{stageRunId}/stream`，实时渲染打字效果
4. 流结束时，后端写入完整 Message 记录，推送状态变更事件

### 8.2 代码文件实时推送

1. 编码 Agent 每完成一个文件，立即写入 `code_file` 表
2. 推送 WebSocket 事件 `code_file:created`
3. 前端文件树实时更新，用户点击可在 Monaco Editor 中预览
4. `WAITING_APPROVAL` 期间用户可在 Monaco 中手动修改，修改直接 PATCH `/code-files/{id}`

### 8.3 测试沙箱

1. 每个 PipelineRun 对应一个 Docker 容器（按需创建，完成后销毁）
2. 测试执行通过 Docker SDK 启动，stdout/stderr 通过 WebSocket 流式输出到 xterm.js
3. 退出码 0 → 测试通过；非 0 → 提取错误信息，触发自动修复循环
4. 容器资源限制：2 CPU、2GB 内存、30 分钟超时

### 8.4 Context 传递（阶段间）

每个 Agent 调用时，System Prompt 中注入：
- 用户原始需求
- 项目 tech_stack
- 前序所有已批准 Artifact 的 content
- 当前阶段的所有历史 Message（对话上下文）

---

## 9. Phase 1 范围（MVP）

**包含：**
- 用户注册 / 登录（邮箱验证码）
- 项目管理（CRUD）
- 需求分析 + 实施规划阶段（完整 Human-in-the-Loop）
- 代码生成阶段（Monaco Editor + 文件树 + 实时流式）
- 测试验证阶段（Docker 沙箱 + xterm.js + 自动修复循环）
- Agent 配置页（调整 system prompt + 模型选择）
- Docker Compose 一键本地启动

**Phase 2 预留（不在本次范围内）：**
- 运维部署阶段
- 多用户 / 团队协作（数据模型已预留 `user_id`）
- RAG / 知识库（pgvector 已引入）
- 云端 Agent 运行时

---

## 10. 产品命名

**DevFlow** — AI 驱动的软件研发工作台

- Dev：面向开发者，研发流程
- Flow：流水线，流畅，无阻断
- 域名建议：devflow.app / devflow.dev

---

## 附：与 Multica 对比

| 维度 | Multica | DevFlow |
|------|---------|---------|
| 核心实体 | Issue（任务） | PipelineRun（需求→代码流水线） |
| 目标用户 | 2-10 人 AI 原生团队 | 个人开发者 |
| Agent 角色 | 辅助处理 Issue | 主导研发各阶段 |
| 后端 | Go + Chi | Java + Spring Boot 3 |
| 实时通信 | gorilla/websocket | Spring WebSocket + STOMP |
| 代码编辑 | 无 | Monaco Editor |
| 终端 | 无 | xterm.js + Docker PTY |
| 多平台 | Web + Electron Desktop | Web only（Phase 1） |
| 工作区 | 多工作区隔离 | 单用户（预留扩展） |
