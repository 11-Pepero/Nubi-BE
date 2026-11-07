package dev.hidly.nubi.common.response;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.servlet.ServletException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<GlobalResponse<?>> handleGlobalException(GlobalException e) {
        HttpStatus status = HttpStatus.valueOf(e.getErrorCode().getHttpStatus());

        return ResponseEntity
                .status(status)
                .body(GlobalResponse.error(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        log.warn("[handleValidationException] 요청 값 검증 실패: {}", detail);

        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalResponse<?>> handleNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[handleNotReadableException] 요청 본문을 읽을 수 없음: {}", e.getMessage());

        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<GlobalResponse<?>> handleTypeMismatchException(TypeMismatchException e) {
        log.warn("[handleTypeMismatchException] 파라미터 타입 불일치: {}", e.getMessage());

        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<GlobalResponse<?>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("[handleMaxUploadSizeExceededException] 업로드 용량 초과: {}", e.getMessage());

        return toResponse(ErrorCode.IMAGE_SIZE_EXCEEDED);
    }

    @ExceptionHandler({ServletException.class, ErrorResponseException.class})
    public ResponseEntity<GlobalResponse<?>> handleErrorResponse(Exception e) {
        if (!(e instanceof ErrorResponse errorResponse)) {
            return handleException(e);
        }

        int status = errorResponse.getStatusCode().value();
        ErrorCode errorCode = switch (status) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
        };

        if (status >= 500) {
            log.error("[handleErrorResponse] 서버 오류 발생", e);
        } else {
            log.warn("[handleErrorResponse] 클라이언트 요청 오류 ({}): {}", status, e.getMessage());
        }

        return ResponseEntity
                .status(status)
                .headers(errorResponse.getHeaders())
                .body(GlobalResponse.error(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalResponse<?>> handleException(Exception e) {
        log.error("[handleException] 처리되지 않은 예외 발생", e);

        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<GlobalResponse<?>> toResponse(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(GlobalResponse.error(errorCode));
    }
}
