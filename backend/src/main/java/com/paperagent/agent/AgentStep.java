package com.paperagent.agent;

/** 一次任务中某个 Agent 的执行摘要；不保存论文正文或模型回复。 */
public record AgentStep(String agentId, String status, String summary) {
}
