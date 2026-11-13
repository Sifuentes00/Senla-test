package com.matvey.senlatest.exception;

public class BinanceApiException extends RuntimeException {

    public BinanceApiException(String message) {
        super(message);
    }

    public BinanceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
