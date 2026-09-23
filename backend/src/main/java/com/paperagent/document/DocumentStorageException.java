package com.paperagent.document;

/** 本地文件读写失败时抛出的异常。 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
