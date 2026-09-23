package com.paperagent.common;

import java.time.Instant;
import java.util.Map;

/** 统一错误格式，让客户端不用猜测各种异常返回结构。 */
public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant time
) {
}

