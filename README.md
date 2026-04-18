# DevFlow

AI 驱动的全流程软件研发工作台。用户输入一个需求，平台通过多个专职 AI Agent 自动推进需求分析 → 实施规划 → 代码生成 → 测试验证全流程，最终输出可运行的代码。

## 核心特性

- **Pipeline 驱动**：一个需求对应一次 Pipeline 执行，分阶段自动推进
- **Human-in-the-Loop**：关键节点（提问、方案选择、审批）人工介入
- **实时流式推送**：Agent 输出通过 WebSocket 实时推送到前端
- **Docker 测试沙箱**：测试阶段在隔离容器中执行，失败自动修复

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Next.js 15 + TypeScript + Tailwind CSS + shadcn/ui |
| 后端 | Spring Boot 3 + Java 21 |
| 数据库 | PostgreSQL 17 + Flyway |
| 实时通信 | Spring WebSocket + STOMP |
| AI 模型 | Anthropic Claude API |
| 测试沙箱 | Docker |

## Docker 一键部署

```bash
# 1. 克隆项目
git clone https://github.com/YinZiyuan/dev-flow.git
cd dev-flow

# 2. 配置环境变量（可选）
export CLAUDE_API_KEY="your-anthropic-api-key"
export JWT_SECRET="your-jwt-secret-at-least-32-chars"

# 3. 一键启动全部服务
docker compose up -d --build

# 4. 访问应用
open http://localhost:3000
```

服务说明：
- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **PostgreSQL**: localhost:5432 (devflow/devflow)

## 本地开发

### 后端

```bash
cd devflow-backend

# 启动 PostgreSQL
docker compose up -d postgres

# 运行应用
./gradlew bootRun
```

### 前端

```bash
cd devflow-frontend
npm install
npm run dev
# 访问 http://localhost:3000
```

前端开发时代理配置：API 请求自动转发到 `http://localhost:8080`。

## 项目结构

```
devflow/
├── devflow-backend/          # Spring Boot 3 后端
│   ├── src/main/java/com/devflow/
│   │   ├── auth/             # JWT 认证
│   │   ├── domain/           # 领域实体 (user, project, pipeline)
│   │   ├── engine/           # Pipeline 状态机 + Stage 执行器
│   │   ├── realtime/         # WebSocket 事件推送
│   │   ├── ai/               # Claude API 流式客户端
│   │   └── sandbox/          # Docker 测试沙箱
│   ├── src/main/resources/db/migration/  # Flyway 迁移
│   └── Dockerfile
├── devflow-frontend/         # Next.js 15 前端
│   ├── app/                  # App Router 路由
│   ├── components/           # UI 组件
│   ├── hooks/                # 自定义 Hooks
│   ├── lib/                  # API 客户端 + 工具
│   ├── store/                # Zustand 状态管理
│   └── Dockerfile
└── docker-compose.yml        # 一键部署编排
```

## 当前进度

- [x] 项目脚手架 (Task 1)
- [x] 数据库 Schema + 迁移 (Task 2)
- [x] JWT 认证 + 用户系统 (Task 3)
- [x] 项目管理 CRUD (Task 4)
- [x] Pipeline 领域实体 (Task 5)
- [x] 状态机引擎 (Task 6)
- [x] REST API + Human-in-the-Loop (Task 7)
- [x] Claude AI 客户端 (Task 8)
- [x] Stage 执行器 — 需求/规划/编码/测试 (Task 9-11)
- [x] Agent 配置 API + 全局异常处理 (Task 12)
- [x] Next.js 15 前端 scaffold + Auth (Task A-B)
- [x] 项目仪表盘 + 详情页 (Task C)
- [x] Pipeline 工作台（三栏布局 + WebSocket）(Task D-E)
- [x] Agent 配置页面 + 完善 (Task F)
- [x] Docker 一键部署

## 文档

- [产品设计文档](docs/superpowers/specs/2026-04-17-devflow-design.md)
- [后端执行计划](docs/superpowers/plans/2026-04-17-devflow-backend.md)
