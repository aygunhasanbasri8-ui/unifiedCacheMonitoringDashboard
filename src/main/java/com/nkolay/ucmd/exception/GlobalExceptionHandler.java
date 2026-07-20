package com.nkolay.ucmd.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import com.nkolay.ucmd.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("Illegal argument: {}", e.getMessage());
        return new ErrorResponse(
                "about:blank/bad-request",
                "Invalid Request",
                HttpStatus.BAD_REQUEST.value(),
                e.getMessage()
        );
    }



    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneralException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return new ErrorResponse(
                "about:blank/internal-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred"
        );
    }


    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleNullPointer(NullPointerException e, HttpServletRequest request) {
        logger.error("Null pointer exception: {}", e.getMessage(), e);
        return new ErrorResponse(
                "about:blank/internal-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Null value encountered"
        );
    }

    @ExceptionHandler(InstanceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleInstanceNotFound(InstanceNotFoundException e) {
        logger.warn("Instance not found: {}", e.getMessage());
        return new ErrorResponse("about:blank/not-found", "Instance Not Found",
                HttpStatus.NOT_FOUND.value(), e.getMessage());
    }

    @ExceptionHandler(InstanceUnreachableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleUnreachable(InstanceUnreachableException e) {
        logger.warn("Instance unreachable: {}", e.getMessage());
        return new ErrorResponse("about:blank/unreachable", "Instance Unreachable",
                HttpStatus.SERVICE_UNAVAILABLE.value(), e.getMessage());
    }

    @ExceptionHandler(InvalidPrefixException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidPrefix(InvalidPrefixException e) {
        logger.warn("Invalid prefix: {}", e.getMessage());
        return new ErrorResponse("about:blank/bad-request", "Invalid Prefix",
                HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

}
