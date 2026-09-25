package com.paperagent.agent;

import java.util.List;

/** 审查结论只供用户参考；通过审查也不会自动修改原稿。 */
public record AgentReview(boolean approved, String explanation, List<String> warnings) {
    public AgentReview {
        warnings = List.copyOf(warnings);
    }
}
