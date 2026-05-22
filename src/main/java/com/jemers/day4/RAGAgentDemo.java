package com.jemers.day4;

import java.util.*;
import java.util.function.Function;

/**
 * Day 4 — RAG 与记忆管理
 *
 * 核心思想：
 *   Agent 不能只靠 LLM 内置知识，需要外挂知识库（RAG）。
 *   关键设计：JIT（Just-In-Time）检索 — 只在需要时从记忆库拉取相关片段，
 *   而非将所有历史塞进 Context。
 *
 * 架构升级：
 *   - MemoryService: 模拟向量检索，按 query 返回相关记忆片段
 *   - ContextManager: 增加 System Context 注入能力
 *   - ReAct 循环升级：增加 Action: SEARCH_MEMORY -> Observation -> Synthesis
 *
 * 增长场景映射：
 *   - 用户行为日志 → MemoryService 存储 → Agent 按需检索历史转化数据
 *   - A/B 测试历史结果 → 避免重复实验 → JIT 检索已有结论
 */
public class RAGAgentDemo {

    public static void main(String[] args) {
        System.out.println("=== Day 4: RAG 与记忆管理 ===\n");

        // 1. 初始化记忆库（预存历史数据）
        MemoryService memory = new MemoryService();
        memory.store("2024 Q1 GMV 增长率为 15.2%，主要受春节活动驱动");
        memory.store("2024 Q2 GMV 增长率为 16.8%，618 大促贡献显著");
        memory.store("2023 Q3 GMV 增长率为 12.1%，新渠道拓展见效");
        memory.store("用户留存率：次日 45%，7日 28%，30日 15%");
        memory.store("A/B 测试 #203: 红色 CTA 按钮比蓝色转化率高 3.2%");

        // 2. 构建 Agent
        ContextManager ctx = new ContextManager(2048, memory);
        AgentHarness harness = new AgentHarness(new RAGAwareLLM(), ctx);

        // 3. 执行：Agent 会先检索记忆再回答
        AgentResult result = harness.execute("2024 年 Q3 的 GMV 增长率大概是多少？参考历史趋势给出预测");

        System.out.println("\n=== 执行完成 ===");
        System.out.println("状态: " + result.status());
        System.out.println("答案: " + result.answer());
    }
}

// ============ DTO ============

record AgentResult(String status, String answer, int steps) {}

record Decision(String action, String toolName, String toolInput, String answer) {
    boolean isToolCall() { return "tool_use".equals(action); }
    boolean isFinalAnswer() { return "final_answer".equals(action); }
    boolean isSearchMemory() { return "search_memory".equals(action); }
}

enum AgentState { IDLE, RUNNING, WAITING_TOOL, SEARCHING_MEMORY, FINISHED, FAILED }

// ============ 接口 ============

interface LLM {
    String generate(String systemPrompt, String userPrompt);
}

interface Tool {
    String execute(String input);
}

// ============ 记忆服务 ============

class MemoryService {
    private final List<String> memories = new ArrayList<>();

    public void store(String content) {
        memories.add(content);
        System.out.println("[Memory] 存储: " + content.substring(0, Math.min(30, content.length())) + "...");
    }

    /**
     * 模拟向量检索：关键词匹配
     * 实际项目替换为向量数据库（Milvus/Pinecone/Weaviate）
     */
    public List<String> search(String query, int topK) {
        System.out.println("[Memory] 检索: " + query);
        String[] keywords = query.toLowerCase().split("\\s+");
        List<ScoredMemory> scored = new ArrayList<>();

        for (String mem : memories) {
            int score = 0;
            String lower = mem.toLowerCase();
            for (String kw : keywords) {
                if (lower.contains(kw)) score++;
            }
            if (score > 0) {
                scored.add(new ScoredMemory(score, mem));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(scored.get(i).content());
        }
        return results;
    }

    record ScoredMemory(int score, String content) {}
}

// ============ Context 管理器（增强版） ============

class ContextManager {
    private final List<String> messages = new ArrayList<>();
    private final int maxTokens;
    private final MemoryService memoryService;
    private String systemContext = "";

    public ContextManager(int maxTokens, MemoryService memoryService) {
        this.maxTokens = maxTokens;
        this.memoryService = memoryService;
    }

    /** 注入 System Context（从记忆库 JIT 检索后拼接） */
    public void injectSystemContext(String query) {
        List<String> memories = memoryService.search(query, 3);
        if (!memories.isEmpty()) {
            StringBuilder sb = new StringBuilder("## 相关历史记忆\n");
            for (int i = 0; i < memories.size(); i++) {
                sb.append(i + 1).append(". ").append(memories.get(i)).append("\n");
            }
            systemContext = sb.toString();
            System.out.println("[Context] 注入 " + memories.size() + " 条记忆片段");
        }
    }

    public String getSystemContext() { return systemContext; }

    public void addUserMessage(String msg) { messages.add("USER: " + msg); }
    public void addAssistantMessage(String msg) { messages.add("ASSISTANT: " + msg); }
    public void addToolResult(String toolName, String result) { messages.add("TOOL[" + toolName + "]: " + result); }

    public String getContext() {
        StringBuilder sb = new StringBuilder();
        if (!systemContext.isEmpty()) {
            sb.append(systemContext).append("\n");
        }
        int start = Math.max(0, messages.size() - 8);
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

// ============ Harness ============

class AgentHarness {
    private final LLM llm;
    private final ContextManager contextManager;
    private final Map<String, Tool> toolRegistry = new HashMap<>();
    private final int maxSteps = 6;
    private AgentState state = AgentState.IDLE;

    public AgentHarness(LLM llm, ContextManager contextManager) {
        this.llm = llm;
        this.contextManager = contextManager;
    }

    public void registerTool(String name, Function<String, String> fn) {
        toolRegistry.put(name, fn::apply);
    }

    public AgentResult execute(String task) {
        state = AgentState.RUNNING;
        contextManager.addUserMessage(task);

        // JIT 检索：在回答前先从记忆库拉取相关内容
        contextManager.injectSystemContext(task);
        System.out.println("[Harness] 任务开始: " + task);

        int step = 0;
        String lastAnswer = null;

        while (step < maxSteps && state == AgentState.RUNNING) {
            step++;
            System.out.println("\n--- Step " + step + " ---");

            String context = contextManager.getContext();
            String response = llm.generate(
                "你是一个分析助手。可以使用 tool_use 调用工具，或 final_answer 给出结论。",
                context
            );
            System.out.println("[LLM] " + response);

            Decision decision = parseDecision(response);

            if (decision.isSearchMemory()) {
                state = AgentState.SEARCHING_MEMORY;
                List<String> results = contextManager.memoryService.search(decision.toolInput(), 2);
                String memoryOutput = String.join("\n", results);
                System.out.println("[Memory] 检索结果: " + memoryOutput);
                contextManager.addAssistantMessage(response);
                contextManager.addToolResult("memory", memoryOutput);
                state = AgentState.RUNNING;
            } else if (decision.isToolCall()) {
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
            }
        }

        return new AgentResult(state.name(), lastAnswer, step);
    }

    private Decision parseDecision(String response) {
        if (response.contains("search_memory") || response.contains("检索")) {
            return new Decision("search_memory", "memory", "GMV 增长率", null);
        } else if (response.contains("search")) {
            return new Decision("tool_use", "search", "GMV", null);
        }
        return new Decision("final_answer", null, null, response);
    }

    private String executeToolSafe(String name, String input) {
        try {
            Tool tool = toolRegistry.get(name);
            if (tool == null) return "ERROR: Tool not found: " + name;
            return tool.execute(input);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}

// ============ 模拟实现 ============

class RAGAwareLLM implements LLM {
    private int step = 0;

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        step++;
        if (step == 1) {
            return "让我先检索历史记忆中的 GMV 增长率数据";
        }
        return "根据历史数据（Q1:15.2%, Q2:16.8%, 去年Q3:12.1%），" +
               "趋势显示逐季增长。预测 2024 Q3 GMV 增长率约 17.5%~18.5%。";
    }
}
