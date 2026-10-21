package com.nkolay.ucmd.exception;

import com.nkolay.ucmd.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** RFC 7807 problem type URI tabanı. */
    private static final String PROBLEM_BASE = "https://ucmd.nkolay.local/problems/";

    private static final String TRACE_HEADER = "X-Request-Id";


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("Illegal argument: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "bad-request", "Invalid Request",
                e.getMessage(), request);
    }

    @ExceptionHandler(InstanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstanceNotFound(
            InstanceNotFoundException e, HttpServletRequest request) {
        logger.warn("Instance not found: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, "instance-not-found", "Instance Not Found",
                e.getMessage(), request);
    }

    @ExceptionHandler(InstanceUnreachableException.class)
    public ResponseEntity<ErrorResponse> handleUnreachable(
            InstanceUnreachableException e, HttpServletRequest request) {
        logger.warn("Instance unreachable: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "instance-unreachable", "Instance Unreachable",
                e.getMessage(), request);
    }

    @ExceptionHandler(InvalidPrefixException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPrefix(
            InvalidPrefixException e, HttpServletRequest request) {
        logger.warn("Invalid prefix: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "invalid-prefix", "Invalid Prefix",
                e.getMessage(), request);
    }

    @ExceptionHandler(MapNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleMapNotAllowed(
            MapNotAllowedException e, HttpServletRequest request) {
        logger.warn("Map not allowed: {}", e.getMessage());
        return build(HttpStatus.FORBIDDEN, "map-not-allowed", "Map Not Allowed",
                e.getMessage(), request);
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(
            NullPointerException e, HttpServletRequest request) {
        logger.error("Null pointer exception: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "Null value encountered", request);
    }


    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitExceededException e, HttpServletRequest request) {
        logger.warn("Rate limit exceeded: {}", e.getMessage());
        return build(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", "Too Many Requests",
                "Bu instance için sorgu limiti aşıldı, lütfen biraz bekleyin", request);
    }

    @ExceptionHandler(InstanceBusyException.class)
    public ResponseEntity<ErrorResponse> handleInstanceBusy(
            InstanceBusyException e, HttpServletRequest request) {
        logger.warn("Instance busy: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "instance-busy", "Instance Busy",
                "Bu instance üzerinde eş zamanlı sorgu limiti dolu", request);
    }

    @ExceptionHandler(CircuitOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(
            CircuitOpenException e, HttpServletRequest request) {
        logger.warn("Circuit open: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "circuit-open", "Instance Temporarily Unavailable",
                "Bu instance tekrarlayan hatalar nedeniyle geçici olarak devre dışı", request);
    }

    @ExceptionHandler(MonitorTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleMonitorTimeout(
            MonitorTimeoutException e, HttpServletRequest request) {
        logger.warn("Monitor timeout: {}", e.getMessage());
        return build(HttpStatus.GATEWAY_TIMEOUT, "monitor-timeout", "Operation Timed Out",
                "Sorgu zaman aşımına uğradı", request);
    }


    /** Gövde okunamadı: bozuk JSON, eksik body, tip hatası. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        logger.warn("Unreadable request body on {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "malformed-body", "Malformed Request Body",
                "Request body is missing or not valid JSON", request);
    }

    /** Path/query parametresi beklenen tipe çevrilemedi (örn. {id} = 'abc'). */
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            TypeMismatchException e, HttpServletRequest request) {
        logger.warn("Type mismatch on {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "type-mismatch", "Parameter Type Mismatch",
                "A request parameter has an invalid format", request);
    }

    /** Zorunlu header veya request param eksik. */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleBindingError(
            ServletRequestBindingException e, HttpServletRequest request) {
        logger.warn("Request binding error on {}: {}", request.getRequestURI(), e.getMessage());
        return build(HttpStatus.BAD_REQUEST, "missing-parameter", "Missing Required Parameter",
                e.getMessage(), request);
    }

    /** Uç var ama bu HTTP metodu desteklenmiyor. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.warn("Method {} not supported on {}", e.getMethod(), request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method Not Allowed",
                "HTTP method " + e.getMethod() + " is not supported for this endpoint", request);
    }

    /** Content-Type desteklenmiyor. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        logger.warn("Unsupported media type on {}: {}", request.getRequestURI(), e.getContentType());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type",
                "Unsupported Media Type", "Content-Type is not supported", request);
    }

    /** Böyle bir uç yok (static resource çözümleyici). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException e, HttpServletRequest request) {
        logger.debug("No resource found: {}", request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "endpoint-not-found", "Endpoint Not Found",
                "No endpoint mapped for this path", request);
    }

    /** Böyle bir uç yok (throw-exception-if-no-handler-found açıksa). */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(
            NoHandlerFoundException e, HttpServletRequest request) {
        logger.debug("No handler found: {}", request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "endpoint-not-found", "Endpoint Not Found",
                "No endpoint mapped for this path", request);
    }

    /** Catch-all — en genel handler, her zaman en sonda kalmalı. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception e, HttpServletRequest request) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", request);
    }


    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                String typeSuffix,
                                                String title,
                                                String detail,
                                                HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                PROBLEM_BASE + typeSuffix,
                title,
                status.value(),
                detail,
                request != null ? request.getRequestURI() : null,
                resolveTraceId(request)
        );
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader(TRACE_HEADER);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return UUID.randomUUID().toString();
    }
}