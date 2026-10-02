package com.oracul.app.common;

import com.oracul.app.api.model.ApiError;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String NOT_JSON = "Request body is not valid JSON";
    private static final List<String> FIELD_ORDER =
        List.of("realism", "darkness", "optimism", "horizon", "wildcards", "customWildcards", "output");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException e) {
        return respond(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> handleUnreadable(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", NOT_JSON);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalid(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldErrors().stream()
            .min((a, b) -> Integer.compare(rank(a.getField()), rank(b.getField())))
            .orElse(null);
        String message = first == null ? "request is invalid" : messageFor(first.getField());
        if (first != null && List.of("wildcards", "customWildcards", "output").contains(root(first.getField()))
            && e.getBindingResult().getTarget() instanceof com.oracul.app.api.model.ScenarioConfiguration cfg) {
            message = com.oracul.app.scenario.WildcardRules.firstViolation(cfg.getWildcards()).orElse(message);
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        if ("runId".equals(e.getName())) {
            return respond(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Future not found");
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "request is invalid");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed");
    }

    @ExceptionHandler({HttpMediaTypeNotAcceptableException.class, MissingServletRequestParameterException.class,
        ServletRequestBindingException.class})
    ResponseEntity<ApiError> handleClientError(Exception e) {
        HttpStatus status = e instanceof HttpMediaTypeNotAcceptableException
            ? HttpStatus.NOT_ACCEPTABLE : HttpStatus.BAD_REQUEST;
        String code = status == HttpStatus.NOT_ACCEPTABLE ? "NOT_ACCEPTABLE" : "VALIDATION_FAILED";
        return respond(status, code, "request is invalid");
    }

    @ExceptionHandler({org.springframework.web.ErrorResponseException.class,
        org.springframework.web.bind.support.WebExchangeBindException.class,
        org.springframework.web.multipart.MultipartException.class})
    ResponseEntity<ApiError> handleFrameworkError(Exception e) {
        HttpStatus status = e instanceof org.springframework.web.ErrorResponse er
            ? HttpStatus.resolve(er.getStatusCode().value()) : HttpStatus.BAD_REQUEST;
        if (status == null || status.is5xxServerError()) {
            status = HttpStatus.BAD_REQUEST;
        }
        return respond(status, "VALIDATION_FAILED", "request is invalid");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return handleFrameworkError(e);
        }
        log.error("Unexpected failure", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong — try again");
    }

    private static int rank(String path) {
        int idx = FIELD_ORDER.indexOf(root(path));
        return idx < 0 ? FIELD_ORDER.size() : idx;
    }

    private static String root(String path) {
        int cut = path.length();
        for (char c : new char[] {'.', '['}) {
            int i = path.indexOf(c);
            if (i >= 0) cut = Math.min(cut, i);
        }
        return path.substring(0, cut);
    }

    private static String messageFor(String path) {
        return switch (path) {
            case "realism", "darkness", "optimism" -> path + " must be between 1 and 10";
            case "horizon" -> "unknown horizon";
            default -> path + " is invalid";
        };
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ApiError(code, message));
    }
}
