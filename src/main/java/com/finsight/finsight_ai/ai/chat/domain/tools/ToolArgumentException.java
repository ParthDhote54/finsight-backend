package com.finsight.finsight_ai.ai.chat.domain.tools;

public class ToolArgumentException extends RuntimeException {

    private final String code;
    private final String field;

    public ToolArgumentException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
