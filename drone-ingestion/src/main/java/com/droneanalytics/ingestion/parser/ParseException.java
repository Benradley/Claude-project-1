package com.droneanalytics.ingestion.parser;

/**
 * Thrown when a log file is structurally invalid and cannot be parsed.
 */
public class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
