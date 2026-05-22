package com.jemers.day6;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Day 6 — 自动化评测与 Benchmark 设计
 *
 * 核心思想：
 *   Agent 的输出质量必须量化。单次 Evaluator 只能拦截错误，
 *   批量评测 (Benchmark) 才能在迭代前后对比质量变化。
 *
 * 关键组件：
 *   - TestCase: 输入、预期输出、评分规则、权重
 *   - EvaluationSuite: 测试用例集管理（Golden Dataset）
 *   - Scorer: 评分策略接口（精确匹配、规则检查、语义相似度模拟）
 *   - BenchmarkRunner: 执行评测、生成报告、统计指标
 *
 * 增长场景映射：
 *   - Agent 生成的 Push 文案 → 评测集对比历史高转化文案基线
 *   - 策略输出 → 规则检查是否包含核心 KPI（留存、GMV、ROI）
 *   - 迭代回归：Prompt 修改后自动跑评测集，防止退化
 */
public class AgentEvaluationDemo {

    public static void main(String[] args) {
        System.out.println("=== Day 6: 自动化评测与 Benchmark ===\n");

        // 1. 构建评测集（Golden Dataset）
        EvaluationSuite suite = new EvaluationSuite();
        suite.addTestCase(
            "查询 GMV 增长率",
            "2024 年 Q3 GMV 增长率是多少？",
            "18.3%",
            ScorerType.EXACT_MATCH,
            1.0
        );
        suite.addTestCase(
            "文案包含 CTA",
            "为双十一活动写一句 Push 文案",
            "限时|今天|仅剩|抢购",
            ScorerType.REGEX_MATCH,
            0.8
        );
        suite.addTestCase(
            "策略包含 KPI",
            "制定 Q4 用户增长策略",
            "GMV|留存|转化|ROI",
            ScorerType.KEYWORD_COVERAGE,
            1.0
        );
        suite.addTestCase(
            "数据不幻觉",
            "我们去年用户总数是多少？",
            "未知|需要查询|不在记忆中",
            ScorerType.NEGATIVE_MATCH,
            1.0
        );

        System.out.println("评测集包含 " + suite.size() + " 个测试用例\n");

        // 2. 模拟待评测的 Agent（版本 v1.0）
        Function<String, String> agentV1 = (task) -> switch (task) {
            case String t when t.contains("增长率") -> "2024 年 Q3 GMV 增长率为 18.3%";
            case String t when t.contains("Push") -> "双十一限时抢购，今天下单享 5 折优惠！";
            case String t when t.contains("策略") -> "Q4 聚焦提升留存和转化率，目标 GMV 增长 20%，ROI 达到 1:5";
            case String t when t.contains("用户总数") -> "大约 500 万用户吧"; // 幻觉！
            default -> "我无法回答这个问题";
        };

        // 3. 运行评测
        BenchmarkRunner runner = new BenchmarkRunner(suite);
        EvaluationReport reportV1 = runner.run(agentV1, "Agent v1.0");

        // 4. 输出报告
        printReport(reportV1);

        // 5. 模拟修复后的 Agent（版本 v1.1）
        System.out.println("\n\n=== Agent v1.1 迭代后重新评测 ===\n");
        Function<String, String> agentV2 = (task) -> switch (task) {
            case String t when t.contains("增长率") -> "2024 年 Q3 GMV 增长率为 18.3%";
            case String t when t.contains("Push") -> "双十一限时抢购，今天下单享 5 折优惠！";
            case String t when t.contains("策略") -> "Q4 聚焦提升留存和转化率，目标 GMV 增长 20%，ROI 达到 1:5";
            case String t when t.contains("用户总数") -> "该数据需要查询数据库，当前无法提供准确数字。"; // 修复幻觉
            default -> "我无法回答这个问题";
        };

        EvaluationReport reportV2 = runner.run(agentV2, "Agent v1.1");
        printReport(reportV2);

        // 6. 对比分析
        System.out.println("\n=== 版本对比 ===");
        double delta = reportV2.passRate() - reportV1.passRate();
        System.out.println("通过率变化: " + String.format("%.1f%%", reportV1.passRate() * 100) +
                          " → " + String.format("%.1f%%", reportV2.passRate() * 100) +
                          " (" + (delta > 0 ? "+" : "") + String.format("%.1f", delta * 100) + "pp)");
    }

    private static void printReport(EvaluationReport report) {
        System.out.println("═══════════════════════════════════════");
        System.out.println("评测报告: " + report.agentVersion());
        System.out.println("═══════════════════════════════════════");
        System.out.println("总用例: " + report.totalCases());
        System.out.println("通过: " + report.passed() + " | 失败: " + report.failed());
        System.out.println("通过率: " + String.format("%.1f%%", report.passRate() * 100));
        System.out.println("加权分: " + String.format("%.2f", report.weightedScore() * 100));
        System.out.println("───────────────────────────────────────");

        for (CaseResult cr : report.results()) {
            String icon = cr.passed() ? "✓" : "✗";
            System.out.printf(" %s [%s] %s (得分: %.1f/%.1f)%n",
                icon, cr.scorerType(), cr.caseName(),
                cr.score(), cr.maxScore());
            if (!cr.passed()) {
                System.out.println("    输出: " + cr.actualOutput());
                System.out.println("    预期: " + cr.expectedHint());
            }
        }
    }
}

// ============ 核心架构 ============

enum ScorerType {
    EXACT_MATCH,        // 精确匹配
    REGEX_MATCH,        // 正则匹配
    KEYWORD_COVERAGE,   // 关键词覆盖率
    NEGATIVE_MATCH      // 负向匹配（不应出现的内容）
}

record TestCase(
    String name,
    String input,
    String expected,
    ScorerType scorerType,
    double weight
) {}

record CaseResult(
    String caseName,
    ScorerType scorerType,
    boolean passed,
    double score,
    double maxScore,
    String actualOutput,
    String expectedHint,
    long durationMs
) {}

record EvaluationReport(
    String agentVersion,
    int totalCases,
    int passed,
    int failed,
    double passRate,
    double weightedScore,
    List<CaseResult> results
) {}

// ============ 评测集管理 ============

class EvaluationSuite {
    private final List<TestCase> cases = new ArrayList<>();

    public void addTestCase(String name, String input, String expected,
                            ScorerType type, double weight) {
        cases.add(new TestCase(name, input, expected, type, weight));
    }

    public int size() { return cases.size(); }
    public List<TestCase> getCases() { return List.copyOf(cases); }
}

// ============ 评分器 ============

interface Scorer {
    double score(String actual, String expected);
}

class ExactMatchScorer implements Scorer {
    @Override
    public double score(String actual, String expected) {
        return actual.trim().contains(expected.trim()) ? 1.0 : 0.0;
    }
}

class RegexMatchScorer implements Scorer {
    @Override
    public double score(String actual, String expected) {
        String[] patterns = expected.split("\\|");
        for (String pattern : patterns) {
            if (actual.matches("(?i).*" + pattern.trim() + ".*")) {
                return 1.0;
            }
        }
        return 0.0;
    }
}

class KeywordCoverageScorer implements Scorer {
    @Override
    public double score(String actual, String expected) {
        String[] keywords = expected.split("\\|");
        int matched = 0;
        String lower = actual.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.trim().toLowerCase())) matched++;
        }
        return (double) matched / keywords.length;
    }
}

class NegativeMatchScorer implements Scorer {
    @Override
    public double score(String actual, String expected) {
        // 期望是"不知道/查不到"，如果模型编造了具体数字则判负
        String[] safeResponses = expected.split("\\|");
        String lower = actual.toLowerCase();
        for (String safe : safeResponses) {
            if (lower.contains(safe.trim().toLowerCase())) {
                return 1.0; // 承认不知道 = 通过
            }
        }
        // 检查是否包含具体数字（幻觉信号）
        if (actual.matches(".*\\d+.*")) {
            return 0.0; // 编造数字 = 失败
        }
        return 0.5; // 其他情况部分得分
    }
}

// ============ Benchmark Runner ============

class BenchmarkRunner {
    private final EvaluationSuite suite;
    private final Map<ScorerType, Scorer> scorers;

    public BenchmarkRunner(EvaluationSuite suite) {
        this.suite = suite;
        this.scorers = Map.of(
            ScorerType.EXACT_MATCH, new ExactMatchScorer(),
            ScorerType.REGEX_MATCH, new RegexMatchScorer(),
            ScorerType.KEYWORD_COVERAGE, new KeywordCoverageScorer(),
            ScorerType.NEGATIVE_MATCH, new NegativeMatchScorer()
        );
    }

    public EvaluationReport run(Function<String, String> agent, String version) {
        System.out.println("开始评测: " + version);
        List<CaseResult> results = new ArrayList<>();
        int passed = 0;
        double weightedSum = 0;
        double totalWeight = 0;

        for (TestCase tc : suite.getCases()) {
            long start = System.currentTimeMillis();
            Scorer scorer = scorers.getOrDefault(tc.scorerType(), (a, e) -> 0.0);
            String output = agent.apply(tc.input);
            double score = scorer.score(output, tc.expected);
            long duration = System.currentTimeMillis() - start;

            boolean isPassed = score >= 0.99; // 阈值判断
            if (isPassed) passed++;

            weightedSum += score * tc.weight();
            totalWeight += tc.weight();

            results.add(new CaseResult(
                tc.name(), tc.scorerType(), isPassed,
                score, tc.weight(), output, tc.expected(), duration
            ));

            String icon = isPassed ? "✓" : "✗";
            System.out.printf("  %s %s (%.0fms)%n", icon, tc.name(), duration);
        }

        double passRate = (double) passed / suite.size();
        double weightedScore = totalWeight > 0 ? weightedSum / totalWeight : 0;

        return new EvaluationReport(
            version, suite.size(), passed, suite.size() - passed,
            passRate, weightedScore, results
        );
    }
}
