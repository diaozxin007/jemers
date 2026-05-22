package com.jemers.day2;

import java.util.*;
import java.util.function.Function;

/**
 * Day 2 — Agent Loop: Worker + Evaluator 闭环
 *
 * 核心思想：
 *   Worker（概率性）输出答案，Evaluator（确定性）拦截验证。
 *   错误时 Worker 接收反馈重试，而非直接暴露给外部系统。
 *
 * 关键组件：
 *   - Worker Agent: 尝试解决问题（模拟 LLM 输出）
 *   - Evaluator: 验证答案正确性（确定性规则）
 *   - Feedback Loop: 错误时传递反馈，触发重试
 */
public class AgentLoopDemo {

    public static void main(String[] args) {
        System.out.println("=== Day 2: Agent Loop (Worker + Evaluator) ===\n");

        AgentLoop loop = new AgentLoop(new MockLLM(), new MathEvaluator());
        AgentResult result = loop.run("查询 2024 年 Q3 的 GMV 增长率", null);

        System.out.println("\n=== 最终结果 ===");
        System.out.println("状态: " + result.status());
        System.out.println("答案: " + result.answer());
        System.out.println("迭代次数: " + result.attempts());
    }
}

// ============ 核心架构 ============

record Task(String description, String expectedResult) {}
record Feedback(String message, boolean passed) {}
record AgentResult(String status, String answer, int attempts) {}

interface LLM {
    String generate(String prompt);
}

interface Evaluator {
    Feedback evaluate(String answer, String expected);
}

class AgentLoop {
    private final LLM llm;
    private final Evaluator evaluator;
    private final int maxAttempts;

    public AgentLoop(LLM llm, Evaluator evaluator) {
        this(llm, evaluator, 4);
    }

    public AgentLoop(LLM llm, Evaluator evaluator, int maxAttempts) {
        this.llm = llm;
        this.evaluator = evaluator;
        this.maxAttempts = maxAttempts;
    }

    public AgentResult run(String task, String expectedResult) {
        int attempt = 0;
        String currentAnswer = null;
        String feedback = null;

        while (attempt < maxAttempts) {
            attempt++;
            System.out.println("--- 第 " + attempt + " 次尝试 ---");

            // 1. Worker 生成答案（有反馈时用反馈）
            String prompt = feedback != null
                    ? "请重新回答问题，注意之前的错误：" + feedback + "\n问题: " + task
                    : "问题: " + task;
            currentAnswer = llm.generate(prompt);
            System.out.println("Worker 输出: " + currentAnswer);

            // 2. Evaluator 验证
            Feedback fb = evaluator.evaluate(currentAnswer, expectedResult);
            System.out.println("Evaluator: " + fb.message());

            if (fb.passed()) {
                return new AgentResult("PASSED", currentAnswer, attempt);
            }

            feedback = fb.message();
        }

        return new AgentResult("FAILED", currentAnswer, attempt);
    }
}

// ============ 模拟实现 ============

/**
 * 模拟 LLM：故意在第一次返回错误答案（演示 Evaluator 拦截能力）
 */
class MockLLM implements LLM {
    private boolean firstAttempt = true;

    @Override
    public String generate(String prompt) {
        if (firstAttempt && prompt.contains("增长率")) {
            firstAttempt = false;
            return "12.5%"; // 错误答案
        }
        firstAttempt = false;
        return "18.3%"; // 正确答案（收到反馈后）
    }
}

/**
 * 确定性 Evaluator：使用代码规则验证，而非 LLM 主观判断
 */
class MathEvaluator implements Evaluator {
    private static final String EXPECTED = "18.3%";

    @Override
    public Feedback evaluate(String answer, String expected) {
        String expectedVal = expected != null ? expected : EXPECTED;

        if (answer.trim().equals(expectedVal)) {
            return new Feedback("答案正确，通过验证", true);
        }
        return new Feedback("答案 " + answer + " 与预期 " + expectedVal + " 不符，请检查数据来源", false);
    }
}
