package com.jemers.day3;

import java.util.*;
import java.util.function.Function;

/**
 * Day 3 — Agent Harness：控制框架
 *
 * 核心思想：
 *   Harness 是 Agent 的运行容器，负责状态机、Context 管理、Tool 沙箱隔离。
 *   Worker 的概率性输出被 Harness 的确定性机制约束。
 *
 * 架构组件：
 *   - AgentState 状态机: IDLE -> RUNNING -> WAITING_TOOL -> FINISHED/FAILED
 *   - ContextManager: 滑动窗口截断，防止 Context 膨胀
 *   - ToolRegistry: Tool 注册表 + 异常隔离沙箱
 *   - AgentHarness: 编排执行流程
 */
public class AgentHarnessDemo {

    public static void main(String[] args) {
        System.out.println("=== Day 3: Agent Harness (Control Framework) ===\n");

        // 1. 构建 Harness
        ContextManager ctx = new ContextManager(1024);
        AgentHarness harness = new AgentHarness(new MockLLM(), ctx);

        // 2. 注册 Tools
        harness.registerTool("search", input -> "搜索结果: " + input + " -> 2024年Q3 GMV 增长率为 18.3%");
        harness.registerTool("calc", input -> "计算结果: 42");

        // 3. 执行任务
        System.out.println("初始状态: " + harness.getState());
        AgentResult result = harness.execute("查询 2024 年 Q3 的 GMV 增长率");

        System.out.println("\n=== 执行完成 ===");
        System.out.println("最终状态: " + result.status());
        System.out.println("答案: " + result.answer());
    }
}

// ============ DTO ============

record AgentResult(String status, String answer, int attempts) {}

record Decision(String action, String toolName, String toolInput, String answer) {
    boolean isToolCall() { return "tool_use".equals(action); }
    boolean isFinalAnswer() { return "final_answer".equals(action); }
}

// ============ 状态机 ============

enum AgentState {
    IDLE, RUNNING, WAITING_TOOL, FINISHED, FAILED
}

// ============ 接口 ============

interface LLM {
    String generate(String systemPrompt, String userPrompt);
}

interface Tool {
    String execute(String input);
}

// ============ Harness 核心 ============

class AgentHarness {
    private final LLM llm;
    private final ContextManager contextManager;
    private final Map<String, Tool> toolRegistry = new HashMap<>();
    private final int maxSteps = 5;
    private AgentState state = AgentState.IDLE;

    public AgentHarness(LLM llm, ContextManager contextManager) {
        this.llm = llm;
        this.contextManager = contextManager;
    }

    public void registerTool(String name, Function<String, String> fn) {
        toolRegistry.put(name, fn::apply);
    }

    public AgentState getState() {
        return state;
    }

    public AgentResult execute(String task) {
        state = AgentState.RUNNING;
        contextManager.addUserMessage(task);
        System.out.println("[Harness] 任务开始: " + task);

        int step = 0;
        String lastAnswer = null;

        while (step < maxSteps && state == AgentState.RUNNING) {
            step++;
            System.out.println("\n--- Step " + step + " ---");

            String context = contextManager.getContext();
            String response = llm.generate(
                "你是一个分析助手。使用 tool_use 调用工具，或 final_answer 给出结论。",
                context
            );
            System.out.println("[LLM] " + response);

            Decision decision = parseDecision(response);

            if (decision.isToolCall()) {
                state = AgentState.WAITING_TOOL;
                String toolOutput = executeToolSafe(decision.toolName(), decision.toolInput());
                System.out.println("[Tool:" + decision.toolName() + "] " + toolOutput);

                contextManager.addAssistantMessage(response);
                contextManager.addToolResult(decision.toolName(), toolOutput);
                state = AgentState.RUNNING;
            } else if (decision.isFinalAnswer()) {
                lastAnswer = decision.answer();
                System.out.println("[Harness] 最终答案: " + lastAnswer);
                state = AgentState.FINISHED;
            } else {
                state = AgentState.FAILED;
                System.out.println("[Harness] 无法解析决策");
            }
        }

        if (state != AgentState.FINISHED) {
            return new AgentResult(state.name(), lastAnswer, step);
        }
        return new AgentResult("FINISHED", lastAnswer, step);
    }

    private Decision parseDecision(String response) {
        if (response.contains("search")) {
            return new Decision("tool_use", "search", "GMV", null);
        } else if (response.contains("calc")) {
            return new Decision("tool_use", "calc", "data", null);
        }
        return new Decision("final_answer", null, null, response);
    }

    private String executeToolSafe(String name, String input) {
        try {
            Tool tool = toolRegistry.get(name);
            if (tool == null) {
                return "ERROR: Tool not found: " + name;
            }
            return tool.execute(input);
        } catch (Exception e) {
            // 沙箱隔离：Tool 异常不崩溃 Harness
            return "ERROR: " + e.getMessage();
        }
    }
}

class ContextManager {
    private final List<String> messages = new ArrayList<>();
    private final int maxTokens;

    public ContextManager(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void addUserMessage(String msg) { messages.add("USER: " + msg); }
    public void addAssistantMessage(String msg) { messages.add("ASSISTANT: " + msg); }
    public void addToolResult(String toolName, String result) { messages.add("TOOL[" + toolName + "]: " + result); }

    public String getContext() {
        // 滑动窗口：保留最近 6 条消息
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, messages.size() - 6);
        for (int i = start; i < messages.size(); i++) {
            sb.append(messages.get(i)).append("\n");
        }
        String result = sb.toString();
        if (result.length() > maxTokens) {
            return result.substring(result.length() - maxTokens);
        }
        return result;
    }
}

// ============ 模拟实现 ============

class MockLLM implements LLM {
    private int step = 0;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        step++;
        if (step == 1) {
            return "我需要调用 search 工具来查询 GMV 数据";
        }
        return "根据搜索结果，2024年Q3 GMV增长率为 18.3%";
    }
}
