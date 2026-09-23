package com.paperagent.common;

import com.paperagent.document.DocumentStorageException;
import com.paperagent.document.DocumentValidationException;
import com.paperagent.document.LatexToolUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将参数校验异常转换为稳定、易读的 JSON。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return new ApiError(
                "VALIDATION_FAILED",
                "提交的数据不符合要求",
                fieldErrors,
                Instant.now()
        );
    }

    @ExceptionHandler(DocumentValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleDocumentValidation(DocumentValidationException exception) {
        return error("INVALID_DOCUMENT", exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(ResourceNotFoundException exception) {
        return error("NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public ApiError handleFileTooLarge() {
        return error("FILE_TOO_LARGE", "上传文件不能超过 100 MB");
    }

    @ExceptionHandler(DocumentStorageException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleStorage(DocumentStorageException exception) {
        return error("STORAGE_FAILED", exception.getMessage());
    }

    @ExceptionHandler(LatexToolUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleLatexToolUnavailable(LatexToolUnavailableException exception) {
        return error("LATEX_TOOL_UNAVAILABLE", exception.getMessage());
    }

    private ApiError error(String code, String message) {
        return new ApiError(code, message, Map.of(), Instant.now());
    }
}
