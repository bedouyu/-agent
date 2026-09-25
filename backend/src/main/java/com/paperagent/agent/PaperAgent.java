package com.paperagent.agent;

/** 新增专职 Agent 只需实现此接口，并用 Spring 注册、指定执行顺序。 */
public interface PaperAgent {

    String id();

    String description();

    default boolean supports(AgentContext context) {
        return true;
    }

    void run(AgentContext context);
}
