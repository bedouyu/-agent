package com.paperagent.ai;

import java.util.List;

/** 只报告是否配置了密钥，不把密钥返回给桌面端。 */
public record AiStatusResponse(boolean configured, List<String> models, int maxSelectionCharacters) {
}
