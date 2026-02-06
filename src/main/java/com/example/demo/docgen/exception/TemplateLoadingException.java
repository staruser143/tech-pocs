package com.example.demo.docgen.exception;

public class TemplateLoadingException extends RuntimeException {
    private final String code;
    private final String description;

    public TemplateLoadingException(String code, String description) {
        super(code + ": " + description);
        this.code = code;
        this.description = description;
    }

    public TemplateLoadingException(String code, String description, Throwable cause) {
        super(code + ": " + description, cause);
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
