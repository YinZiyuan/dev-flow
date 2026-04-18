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
| 后端 | Spring Boot 3 + Java 21 |
| 数据库 | PostgreSQL 17 + Flyway |
| 实时通信 | Spring WebSocket + STOMP |
| AI 模型 | Anthropic Claude API |
| 测试沙箱 | Docker |

## 快速启动

```bash
cd devflow-backend

# 启动 PostgreSQL
docker compose up -d

# 运行应用
./gradlew bootRun
```

## 项目结构

```
devflow-backend/
├── src/main/java/com/devflow/
│   ├── auth/          # JWT 认证
│   ├── domain/        # 领域实体 (user, project, pipeline)
│   ├── engine/        # Pipeline 状态机 + Stage 执行器
│   ├── realtime/      # WebSocket 事件推送
│   ├── ai/            # Claude API 客户端 (待实现)
│   └── sandbox/       # Docker 测试沙箱 (待实现)
├── src/main/resources/db/migration/  # Flyway 迁移
└── docker-compose.yml
```

## 当前进度

- [x] 项目脚手架 (Task 1)
- [x] 数据库 Schema + 迁移 (Task 2)
- [x] JWT 认证 + 用户系统 (Task 3)
- [x] 项目管理 CRUD (Task 4)
- [x] Pipeline 领域实体 (Task 5)
- [x] 状态机引擎 (Task 6)
- [x] REST API + Human-in-the-Loop (Task 7)
- [ ] Claude AI 客户端 (Task 8)
- [ ] Stage 执行器 (Task 9-11)
- [ ] Agent 配置 API (Task 12)

## 文档

- [产品设计文档](docs/superpowers/specs/2026-04-17-devflow-design.md)
- [后端执行计划](docs/superpowers/plans/2026-04-17-devflow-backend.md)
