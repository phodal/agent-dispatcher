# Routa Agent Hub — A2A + Koog Integration Example

演示如何使用 A2A 协议 + Koog 框架测试 routa-agent-hub 的功能。

## 架构

```
┌──────────────────────────────────────────────┐
│  A2A Client (Planner/Gate/Worker)           │
└────────────────┬─────────────────────────────┘
                 │ A2A Protocol (JSON-RPC/HTTP)
                 ↓
┌──────────────────────────────────────────────┐
│  AgentHub A2A Server                         │
│  ├─ AgentHubExecutor (12 tools)             │
│  └─ KoogPlannerA2AExecutor (Real LLM Agent) │
└────────────────┬─────────────────────────────┘
                 │
                 ↓
┌──────────────────────────────────────────────┐
│  RoutaSystem                                 │
│  ├─ AgentTools (12 coordination tools)      │
│  ├─ Stores (agent/task/conversation)        │
│  ├─ Coordinator (workflow orchestration)    │
│  └─ EventBus (event pub/sub)                │
└──────────────────────────────────────────────┘
```

## 组件

### 1. Agent Hub A2A Server (端口 9100)

将 routa-agent-hub 的 12 个 agent 管理工具暴露为 A2A 服务：

- `hub/AgentHubExecutor.kt` — A2A executor 接收 JSON 命令，调用 AgentTools
- `hub/AgentHubA2AServer.kt` — A2A 服务器入口

**支持的命令：**
```json
{"command": "list_agents"}
{"command": "create_agent", "name": "worker-1", "role": "CRAFTER"}
{"command": "delegate_task", "agentId": "...", "taskId": "..."}
{"command": "report_to_parent", "agentId": "...", "summary": "..."}
// ... 共 14 个命令
```

### 2. Koog AI Agents (真实 LLM 驱动)

- `agent/KoogPlannerA2AExecutor.kt` — **ROUTA 角色**，使用 LLM 自主规划任务、创建 sub-agents
- `agent/KoogGateA2AExecutor.kt` — **GATE 角色**，使用 LLM 自主验证工作质量

这些是真正的 Koog `AIAgent`，会调用 LLM（Ollama/DeepSeek/...）并自主决策使用哪些工具。

### 3. 模拟 Agents (用于快速测试)

- `planner/PlannerAgentExecutor.kt` — 程序化 planner（连接 Hub，创建 workers，分配任务）
- `worker/WorkerAgentExecutor.kt` — 程序化 worker（接收指令，执行，报告）

## 测试场景

### 场景 1：程序化集成测试（推荐先运行）

```bash
./gradlew :examples:agent-hub-a2a-example:runIntegration
```

**测试内容：**
- 18 个测试用例覆盖全部 12 个 AgentTools
- 验证 A2A 协议层正确性
- 不需要 LLM（纯程序化命令）
- 运行时间：~10 秒

**验证的工具：**
- ✅ initialize, list_agents, create_agent, get_agent_status, get_agent_summary
- ✅ read_agent_conversation, send_message, delegate_task, report_to_parent
- ✅ wake_or_create_task_agent, send_message_to_task_agent
- ✅ subscribe_to_events, unsubscribe_from_events

### 场景 2：真实 AI Agent E2E

```bash
./gradlew :examples:agent-hub-a2a-example:runRealAgents
```

**测试内容：**
- 启动 Koog Planner Agent (ROUTA role) on port 9200
- 启动 Koog Gate Agent (GATE role) on port 9201
- **LLM 自主决策**：创建 agents、分配任务、发送消息、验证工作
- 需要 `~/.autodev/config.yaml` 配置 LLM

**前提：**
```yaml
# ~/.autodev/config.yaml
active: default
configs:
  - name: default
    provider: ollama  # 或 deepseek, openai, anthropic
    model: llama3.2
```

### 场景 3：JUnit 测试

```bash
./gradlew :examples:agent-hub-a2a-example:test
```

单元测试覆盖核心功能。

## 独立运行各服务

```bash
# Terminal 1: 启动 Hub
./gradlew :examples:agent-hub-a2a-example:runHubServer

# Terminal 2: 启动 Planner (模拟版)
./gradlew :examples:agent-hub-a2a-example:runPlannerServer

# Terminal 3: 启动 Worker (模拟版)
./gradlew :examples:agent-hub-a2a-example:runWorkerServer
```

然后用 A2A 客户端（如 curl 或自定义客户端）连接测试。

## 验证结果

### ✅ 程序化测试（runIntegration）

```
Test Results: 18 passed, 0 failed, 18 total
🎉 All tests passed! routa-agent-hub A2A integration is OK
```

### ✅ 真实 AI Agent 测试（runRealAgents）

**Planner Agent (ROUTA) 自主完成：**
- ✅ `list_agents` — 发现当前 workspace 状态
- ✅ `create_agent` (api-developer) — 创建 API 开发者 CRAFTER
- ✅ `create_agent` (test-writer) — 创建测试编写者 CRAFTER  
- ✅ `get_agent_status` x2 — 检查新 agents 的状态
- ✅ `send_message_to_agent` — 向 api-developer 发送任务指令
- ✅ `list_agents` — 确认最终 roster

**Gate Agent (GATE) 自主完成：**
- ✅ `list_agents` — 列出所有 agents
- ✅ `get_agent_status` x3 — 检查每个 agent
- ✅ `get_agent_summary` x3 — 获取工作摘要

LLM 调用了 9-12 个工具，完全自主决策！

## 技术栈

- **Koog** 0.6.2 — AI Agent 框架
- **A2A** (via Koog) — Agent-to-Agent 协议
- **routa-core** — Agent coordination 核心
- **routa-agent-hub** — 12 个 agent 管理工具

## 依赖

已在 `gradle/libs.versions.toml` 添加：
- `koog-a2a-server` / `koog-a2a-client` / `koog-a2a-core`
- `koog-a2a-transport-server-http` / `koog-a2a-transport-client-http`
