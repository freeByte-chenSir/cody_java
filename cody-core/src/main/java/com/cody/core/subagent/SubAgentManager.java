package com.cody.core.subagent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sub-agent orchestration: spawn, kill, resume, get status.
 *
 *
 * Manages concurrent sub-agents, each running in its own thread.
 * Each sub-agent has a type (research/test/code/generic), task description,
 * and runs a simplified agent loop.
 */
public class SubAgentManager {

    private final Map<String, SubAgent> agents = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger agentCounter = new AtomicInteger(1);

    /** Spawn a new sub-agent. Returns the agent ID. */
    public String spawnAgent(String task, String type) {
        String agentId = "agent_" + agentCounter.getAndIncrement();
        SubAgent agent = new SubAgent(agentId, task, type);
        agents.put(agentId, agent);
        executor.submit(agent);
        return agentId;
    }

    /** Get the current status of a sub-agent. */
    public Map<String, Object> getAgentStatus(String agentId) {
        SubAgent agent = agents.get(agentId);
        if (agent == null) {
            return Map.of("error", "Agent not found: " + agentId);
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", agent.agentId);
        status.put("task", agent.task);
        status.put("type", agent.type);
        status.put("state", agent.state);
        status.put("result", agent.result);
        return status;
    }

    /** List all sub-agents. */
    public List<Map<String, Object>> listAgents() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SubAgent a : agents.values()) {
            result.add(getAgentStatus(a.agentId));
        }
        return result;
    }

    /** Kill a running sub-agent. */
    public boolean killAgent(String agentId) {
        SubAgent agent = agents.get(agentId);
        if (agent == null) return false;
        agent.state = AgentState.KILLED;
        agent.future.cancel(true);
        return true;
    }

    /** Resume a paused sub-agent. */
    public boolean resumeAgent(String agentId) {
        SubAgent agent = agents.get(agentId);
        if (agent == null || agent.state != AgentState.PAUSED) return false;
        agent.state = AgentState.RUNNING;
        agent.resumeLatch.countDown();
        return true;
    }

    /** Clean up completed/killed agents. */
    public void cleanup() {
        agents.entrySet().removeIf(e -> {
            AgentState s = e.getValue().state;
            return s == AgentState.COMPLETED || s == AgentState.KILLED || s == AgentState.FAILED;
        });
    }

    public void shutdown() {
        executor.shutdownNow();
        agents.clear();
    }

    // ── Inner Types ──────────────────────────────────────────────────

    enum AgentState { RUNNING, PAUSED, COMPLETED, KILLED, FAILED }

    class SubAgent implements Runnable {
        final String agentId;
        final String task;
        final String type;
        volatile AgentState state = AgentState.RUNNING;
        volatile String result;
        volatile Future<?> future;
        final CountDownLatch resumeLatch = new CountDownLatch(1);

        SubAgent(String agentId, String task, String type) {
            this.agentId = agentId;
            this.task = task;
            this.type = type;
        }

        @Override
        public void run() {
            try {
                // Simplified agent loop: the sub-agent would call the LLM
                // with a restricted toolset and report back.
                // In a full implementation, this creates a nested AgentRunner
                // with SUB_AGENT_TOOLSETS[type].
                state = AgentState.RUNNING;
                result = "[SUB_AGENT_RESULT] Task: " + task + " | Type: " + type + " | (simulated execution)";
                state = AgentState.COMPLETED;
            } catch (Exception e) {
                state = AgentState.FAILED;
                result = "[SUB_AGENT_ERROR] " + e.getMessage();
            }
        }
    }
}
