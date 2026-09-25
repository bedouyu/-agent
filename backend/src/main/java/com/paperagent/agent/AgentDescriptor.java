package com.paperagent.agent;

/** 告诉客户端当前安装了哪些专职 Agent。 */
public record AgentDescriptor(String id, String description) {
}
