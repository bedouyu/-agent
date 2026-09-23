package com.paperagent.common;

/** 请求的项目或文档不存在时抛出的业务异常。 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
