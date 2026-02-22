package com.vish.fno.reader.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class InitialisationExceptionTest {

    @Test
    void testMessageAndCauseConstructor() {
        String message = "Initialisation failed";
        Throwable cause = new RuntimeException("root cause");

        InitialisationException exception = new InitialisationException(message, cause);

        assertEquals(message, exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void testCauseOnlyConstructor() {
        Throwable cause = new IllegalStateException("underlying error");

        InitialisationException exception = new InitialisationException(cause);

        assertSame(cause, exception.getCause());
        assertEquals(cause.toString(), exception.getMessage());
    }

    @Test
    void testIsRuntimeException() {
        InitialisationException exception = new InitialisationException("test", new Exception("cause"));

        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testMessageAndCauseConstructorWithNullCause() {
        String message = "Initialisation failed";

        InitialisationException exception = new InitialisationException(message, null);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }
}
