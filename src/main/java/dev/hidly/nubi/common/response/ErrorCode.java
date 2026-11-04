package dev.hidly.nubi.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_REQUEST("요청 파라미터가 올바르지 않습니다.", 400),
    UNAUTHORIZED("인증에 실패했거나 토큰이 만료되었습니다.", 401),
    PASSWORD_INVALID("비밀번호가 일치하지 않습니다.", 401),
    TOKEN_INVALID("유효하지 않은 토큰입니다.", 401),
    FORBIDDEN("접근 권한이 없는 리소스입니다.", 403),
    NOT_FOUND("요청한 리소스를 찾을 수 없습니다.", 404),
    USER_NOT_FOUND("사용자를 찾을 수 없습니다.", 404),
    METHOD_NOT_ALLOWED("지원하지 않는 HTTP 메서드입니다.", 405),
    NOT_ACCEPTABLE("지원하지 않는 응답 형식입니다.", 406),
    EMAIL_DUPLICATION("이미 존재하는 이메일입니다.", 409),
    UNSUPPORTED_MEDIA_TYPE("지원하지 않는 요청 형식입니다.", 415),
    OCR_EXTRACTION_FAILED("성분표를 인식하지 못했습니다.", 422),
    TOO_MANY_REQUESTS("요청 한도를 초과했습니다.", 429),
    INTERNAL_ERROR("서버 오류가 발생했습니다.", 500);

    private final String message;
    private final int httpStatus;

    ErrorCode(String message, int httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return name();
    }
}
