package dev.hidly.nubi.common.response;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_REQUEST("요청 파라미터가 올바르지 않습니다.", 400),
    IMAGE_TYPE_NOT_ALLOWED("JPG, PNG, WEBP 이미지만 올릴 수 있습니다.", 400),
    IMAGE_SIZE_EXCEEDED("이미지는 한 장에 5MB까지 올릴 수 있습니다.", 400),
    IMAGE_COUNT_EXCEEDED("행사 사진은 1장 이상, 10장까지 올릴 수 있습니다.", 400),
    REVIEW_IMAGE_COUNT_EXCEEDED("후기 사진은 1장 이상, 10장까지 올릴 수 있습니다.", 400),
    IMAGE_ORDER_INVALID("행사의 모든 사진 ID를 한 번씩 보내야 합니다.", 400),
    APPLICATION_NOT_ALLOWED("지금은 이 행사에 바로 신청할 수 없습니다.", 400),
    INVALID_STATUS("지금 신청 상태에서는 바꿀 수 없는 상태입니다.", 400),
    REVIEW_NOT_ALLOWED("참여가 확정되고 행사가 시작된 신청에만 후기를 쓸 수 있습니다.", 400),
    UNAUTHORIZED("인증에 실패했거나 토큰이 만료되었습니다.", 401),
    INVALID_CREDENTIALS("이메일 또는 비밀번호가 올바르지 않습니다.", 401),
    TOKEN_INVALID("유효하지 않은 토큰입니다.", 401),
    FORBIDDEN("접근 권한이 없는 리소스입니다.", 403),
    PROGRAM_ACCESS_DENIED("주최 기관만 행사를 관리할 수 있습니다.", 403),
    NOT_FOUND("요청한 리소스를 찾을 수 없습니다.", 404),
    MEMBER_NOT_FOUND("담당자를 찾을 수 없습니다.", 404),
    GROUP_NOT_FOUND("그룹을 찾을 수 없습니다.", 404),
    SUPPORT_TAG_NOT_FOUND("지원 항목을 찾을 수 없습니다.", 404),
    CARE_PERSON_NOT_FOUND("개별 케어 대상을 찾을 수 없습니다.", 404),
    PROGRAM_NOT_FOUND("행사를 찾을 수 없습니다.", 404),
    VENUE_NOT_FOUND("장소를 찾을 수 없습니다.", 404),
    IMAGE_NOT_FOUND("사진을 찾을 수 없습니다.", 404),
    APPLICATION_NOT_FOUND("신청을 찾을 수 없습니다.", 404),
    REVIEW_NOT_FOUND("후기를 찾을 수 없습니다.", 404),
    NOTIFICATION_NOT_FOUND("알림을 찾을 수 없습니다.", 404),
    METHOD_NOT_ALLOWED("지원하지 않는 HTTP 메서드입니다.", 405),
    NOT_ACCEPTABLE("지원하지 않는 응답 형식입니다.", 406),
    EMAIL_DUPLICATION("이미 존재하는 이메일입니다.", 409),
    APPLICATION_DUPLICATED("이 그룹은 이미 이 행사에 신청했습니다.", 409),
    CAPACITY_EXCEEDED("행사 정원을 넘어 신청할 수 없습니다.", 409),
    REVIEW_DUPLICATED("이 신청에는 이미 후기를 남겼습니다.", 409),
    UNSUPPORTED_MEDIA_TYPE("지원하지 않는 요청 형식입니다.", 415),
    TOO_MANY_REQUESTS("요청 한도를 초과했습니다.", 429),
    INTERNAL_ERROR("서버 오류가 발생했습니다.", 500),
    FILE_UPLOAD_FAILED("파일을 저장하지 못했습니다.", 500);

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
