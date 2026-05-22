# JeMeRS — AI Agent 工程化实战

**Java Enterprise Multi-agent Reinforcement System**

面向 Java 开发者的 AI Agent 工程化实战教程。每天一课，从理论到代码级落地。

## 课程目录

| 课时 | 主题 | 入口类 | 核心概念 |
|------|------|--------|----------|
| Day 1 | 认知与边界 | — | Workflow vs Agent，确定性禁区，代理反馈 |
| Day 2 | Agent Loop | `day2.AgentLoopDemo` | Worker + Evaluator 循环，自我修正 |
| Day 3 | Agent Harness | `day3.AgentHarnessDemo` | 状态机，Context 管理，Tool 沙箱 |
| Day 4 | RAG 与记忆 | `day4.RAGAgentDemo` | 分层记忆，JIT 检索，Context 注入 |
| Day 5 | 多 Agent 协作 | `day5.MultiAgentDemo` | Orchestrator-Workers，路由分发，结果聚合 |
| Day 6 | 自动化评测 | `day6.AgentEvaluationDemo` | Benchmark 设计，评分策略，回归测试 |

## 快速开始

```bash
# Maven 编译
mvn clean compile

# 运行某一课
mvn exec:java -Dexec.mainClass="com.jemers.day2.AgentLoopDemo"
mvn exec:java -Dexec.mainClass="com.jemers.day3.AgentHarnessDemo"
mvn exec:java -Dexec.mainClass="com.jemers.day4.RAGAgentDemo"
mvn exec:java -Dexec.mainClass="com.jemers.day5.MultiAgentDemo"
```

## 导入 IDEA

```
File → Open → 选择本仓库根目录 → 自动识别为 Maven 项目
```
