package com.vish.fno.reader.shoonya.exception;

import java.io.Serial;

/**
 * Exception thrown when the Shoonya API returns an error response ({@code stat: "Not_Ok"}).
 */
public class ShoonyaApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ShoonyaApiException(String message) {
        super(message);
    }

    public ShoonyaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
