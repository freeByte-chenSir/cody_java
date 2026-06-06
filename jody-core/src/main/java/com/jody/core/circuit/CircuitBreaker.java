package com.jody.core.circuit;

import com.jody.core.config.Config;
import com.jody.core.error.JodyErrors.CircuitBreakerError;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-run circuit breaker with token limit, cost limit, step limit, and loop detection.
 * Guards against runaway agents by terminating execution when any threshold is exceeded.
 */
public class CircuitBreaker {

    public static class CircuitState {
        public long totalTokens;
        public int stepCount;
        public double estimatedCost;
        public final Deque<String> recentResults = new ArrayDeque<>();
    }

    public static void check(Config.CircuitBreakerConfig cbc, CircuitState cb) {
        if (!cbc.isEnabled() || cb == null) return;
        if (cbc.getMaxTokens() > 0 && cb.totalTokens > cbc.getMaxTokens()) {
            throw new CircuitBreakerError("Token limit exceeded", cb.totalTokens, cb.estimatedCost);
        }
        if (cbc.getMaxCostUsd() > 0 && cb.estimatedCost > cbc.getMaxCostUsd()) {
            throw new CircuitBreakerError("Cost limit exceeded", cb.totalTokens, cb.estimatedCost);
        }
        if (cbc.getMaxSteps() > 0 && cb.stepCount >= cbc.getMaxSteps()) {
            throw new CircuitBreakerError("Step limit exceeded", cb.totalTokens, cb.estimatedCost);
        }
        if (cb.recentResults.size() >= cbc.getLoopDetectTurns()) {
            double similarity = computeSimilarity(cb.recentResults);
            if (similarity >= cbc.getLoopSimilarityThreshold()) {
                throw new CircuitBreakerError("Agent loop detected", cb.totalTokens, cb.estimatedCost);
            }
        }
    }

    public static void update(CircuitState cb, String resultText, long tokens, double cost) {
        if (cb == null) return;
        cb.totalTokens += tokens;
        cb.stepCount++;
        cb.estimatedCost += cost;
        if (resultText != null) {
            cb.recentResults.addLast(resultText);
        }
    }

    public static void trimRecent(CircuitState cb, int maxRecent) {
        if (cb == null) return;
        while (cb.recentResults.size() > maxRecent) {
            cb.recentResults.removeFirst();
        }
    }

    private static double computeSimilarity(Deque<String> recent) {
        if (recent.size() < 2) return 0.0;
        double maxSim = 0.0;
        String prev = null;
        for (String s : recent) {
            if (prev != null) {
                double sim = similar(prev, s);
                if (sim > maxSim) maxSim = sim;
            }
            prev = s;
        }
        return maxSim;
    }

    private static double similar(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        int shorter = Math.min(a.length(), b.length());
        if (shorter == 0) return 0.0;
        int matches = 0;
        for (int i = 0; i < shorter; i++) {
            if (a.charAt(i) == b.charAt(i)) matches++;
        }
        return (double) matches / Math.max(a.length(), b.length());
    }
}
