package com.paperagent.document;

/** 上传的文件不符合论文文档要求时抛出的异常。 */
public class DocumentValidationException extends RuntimeException {

    public DocumentValidationException(String message) {
        super(message);
    }
}
