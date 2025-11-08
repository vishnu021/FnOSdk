package com.vish.fno.reader.exception;

import java.io.Serial;

public class InitialisationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InitialisationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InitialisationException(Throwable cause) {
        super(cause);
    }
}
