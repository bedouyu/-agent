package com.paperagent.document;

/** 本机没有找到配置的 TeX Live 命令时抛出。 */
public class LatexToolUnavailableException extends RuntimeException {

    public LatexToolUnavailableException(String message) {
        super(message);
    }
}
