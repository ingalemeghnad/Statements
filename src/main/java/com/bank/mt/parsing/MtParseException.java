package com.bank.mt.parsing;

public class MtParseException extends RuntimeException {

    public MtParseException(String message) {
        super(message);
    }

    public MtParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
