package com.paperagent.ai;

import org.springframework.http.HttpStatus;

/** 面向桌面端的可读错误；不包含密钥、论文内容或服务商原始响应。 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AiServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
