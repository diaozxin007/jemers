package com.jemers.day5;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Day 5 — 多 Agent 协作与编排
 *
 * 核心思想：
 *   复杂任务需要多个专业 Agent 分工协作，由 Orchestrator（编排器）统一调度。
 *   关键挑战：路由分发、跨 Agent 状态传递、冲突消解、结果聚合。
 *
 * 协作模式：
 *   1. Orchestrator-Workers: 中央编排，并行/串行分发子任务
 *   2. Evaluator-Optimizer: 多轮迭代优化（Day 2 的扩展）
 *   3. 结果聚合：多数投票、加权融合、冲突检测
 *
 * 架构组件：
 *   - Orchestrator: 任务分解、路由分发、结果聚合
 *   - WorkerAgent: 专业领域 Agent（数据分析、文案生成、策略建议）
 *   - ResultAggregator: 冲突消解、一致性检查、最终输出
 *
 * 增长场景映射：
 *   - 增长方案制定 = 数据分析师(查数) + 文案专家(写文案) + 策略师(定方案)
 *   - Orchestrator 根据任务类型路由到不同 Worker
 *   - 最终由聚合器合并各 Worker 输出为统一方案
 */
public class MultiAgentDemo {

    public static void main(String[] args) {
        System.out.println("=== Day 5: 多 Agent 协作与编排 ===\n");

        // 1. 创建专业 Worker 池
        List<WorkerAgent> workers = List.of(
            new WorkerAgent("data_analyst", "数据分析师",
                "分析用户增长数据，提供 GMV、转化率、留存率等核心指标"),
            new WorkerAgent("copywriter", "文案专家",
                "根据数据洞察撰写增长文案和 Push 通知"),
            new WorkerAgent("strategist", "策略师",
                "基于数据和文案制定完整的增长策略")
        );

        // 2. 构建编排器
        Orchestrator orchestrator = new Orchestrator(workers, new ResultAggregator());

        // 3. 执行复杂任务
        TaskRequest request = new TaskRequest(
            "制定 Q4 用户增长方案",
            List.of("data_analyst", "copywriter", "strategist")
        );

        TaskResult result = orchestrator.execute(request);

        System.out.println("\n=== 编排完成 ===");
        System.out.println("任务: " + request.description());
        System.out.println("状态: " + result.status());
        System.out.println("Worker 响应数: " + result.responses().size());
        System.out.println("最终方案:\n" + result.finalOutput());
    }
}

// ============ DTO ============

record TaskRequest(String description, List<String> requiredWorkers) {}
record TaskResult(String status, List<WorkerResponse> responses, String finalOutput) {}
record WorkerResponse(String workerId, String workerRole, String output, long durationMs) {}

// ============ Worker Agent ============

class WorkerAgent {
    final String id;
    final String role;
    final String capability;

    public WorkerAgent(String id, String role, String capability) {
        this.id = id;
        this.role = role;
        this.capability = capability;
    }

    /** 模拟 Worker 处理子任务 */
    public WorkerResponse execute(String task, String context) {
        long start = System.currentTimeMillis();
        System.out.println("  [" + role + "] 开始处理: " + task);

        // 模拟不同 Worker 的输出
        String output = switch (id) {
            case "data_analyst" ->
                "【数据分析】Q3 GMV 增长率 18.3%，新用户获取成本降低 12%，" +
                "次日留存 45%，7日留存 28%。建议聚焦高价值渠道。";
            case "copywriter" ->
                "【文案方案】Push 文案 A: '你的专属优惠已到账，限时 24 小时！'\n" +
                "Push 文案 B: '老用户专享：满 100 减 30，仅限今天'\n" +
                "建议 A/B 测试两套方案，预计文案 A 打开率更高。";
            case "strategist" ->
                "【增长策略】基于数据：1) 聚焦高留存渠道投放 2) A/B 测试两套 Push 文案\n" +
                "3) 针对 7日留存用户设计召回活动 4) 目标：Q4 GMV 增长 20%+";
            default -> "无法处理此任务";
        };

        long duration = System.currentTimeMillis() - start;
        return new WorkerResponse(id, role, output, duration);
    }
}

// ============ Orchestrator ============

class Orchestrator {
    private final Map<String, WorkerAgent> workerPool;
    private final ResultAggregator aggregator;
    private final ExecutorService executor;

    public Orchestrator(List<WorkerAgent> workers, ResultAggregator aggregator) {
        this.aggregator = aggregator;
        this.workerPool = new HashMap<>();
        for (WorkerAgent w : workers) {
            workerPool.put(w.id, w);
        }
        this.executor = Executors.newFixedThreadPool(workers.size());
    }

    public TaskResult execute(TaskRequest request) {
        System.out.println("[Orchestrator] 接收任务: " + request.description());
        System.out.println("[Orchestrator] 需要 Workers: " + request.requiredWorkers());

        // 1. 路由分发：按任务需求选择 Worker
        List<WorkerAgent> selectedWorkers = request.requiredWorkers().stream()
            .map(workerPool::get)
            .filter(Objects::nonNull)
            .toList();

        System.out.println("[Orchestrator] 实际分配 " + selectedWorkers.size() + " 个 Worker");

        // 2. 并行执行子任务
        List<Future<WorkerResponse>> futures = new ArrayList<>();
        for (WorkerAgent worker : selectedWorkers) {
            Future<WorkerResponse> future = executor.submit(() ->
                worker.execute(request.description(), "")
            );
            futures.add(future);
        }

        // 3. 收集结果
        List<WorkerResponse> responses = new ArrayList<>();
        for (Future<WorkerResponse> f : futures) {
            try {
                WorkerResponse resp = f.get(30, TimeUnit.SECONDS);
                responses.add(resp);
                System.out.println("  [" + resp.workerRole() + "] 完成 (" + resp.durationMs() + "ms)");
            } catch (Exception e) {
                System.err.println("  Worker 执行失败: " + e.getMessage());
            }
        }

        // 4. 聚合结果
        String finalOutput = aggregator.aggregate(responses);

        return new TaskResult("COMPLETED", responses, finalOutput);
    }

    public void shutdown() {
        executor.shutdown();
    }
}

// ============ Result Aggregator ============

class ResultAggregator {

    /**
     * 聚合多个 Worker 的输出为统一方案
     * 策略：按角色顺序拼接 + 冲突检测
     */
    public String aggregate(List<WorkerResponse> responses) {
        System.out.println("\n[Aggregator] 开始聚合 " + responses.size() + " 个 Worker 结果");

        // 按固定角色顺序排列
        Map<String, WorkerResponse> byRole = responses.stream()
            .collect(Collectors.toMap(WorkerResponse::workerId, r -> r));

        StringBuilder sb = new StringBuilder();
        sb.append("=== 增长方案 ===\n\n");

        // 1. 数据先行
        if (byRole.containsKey("data_analyst")) {
            sb.append("## 一、数据洞察\n").append(byRole.get("data_analyst").output()).append("\n\n");
        }

        // 2. 文案跟进
        if (byRole.containsKey("copywriter")) {
            sb.append("## 二、文案方案\n").append(byRole.get("copywriter").output()).append("\n\n");
        }

        // 3. 策略总结
        if (byRole.containsKey("strategist")) {
            sb.append("## 三、执行策略\n").append(byRole.get("strategist").output()).append("\n\n");
        }

        // 冲突检测示例
        checkConsistency(responses);

        return sb.toString();
    }

    /**
     * 一致性检查：检测 Worker 之间的矛盾
     * 例如：分析师说留存差，文案却建议大规模拉新 → 矛盾
     */
    private void checkConsistency(List<WorkerResponse> responses) {
        String data = responses.stream()
            .filter(r -> "data_analyst".equals(r.workerId()))
            .map(WorkerResponse::output)
            .findFirst().orElse("");

        String strategy = responses.stream()
            .filter(r -> "strategist".equals(r.workerId()))
            .map(WorkerResponse::output)
            .findFirst().orElse("");

        // 简单冲突检测：如果数据说留存差但策略建议拉新
        if (data.contains("留存") && strategy.contains("拉新")) {
            System.out.println("[Aggregator] ⚠️ 检测到潜在矛盾：留存数据 vs 拉新策略");
        } else {
            System.out.println("[Aggregator] ✓ 一致性检查通过");
        }
    }
}
