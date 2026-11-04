package dev.hidly.nubi.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class GlobalResponse<T> {
    private final boolean success;
    private final T data;
    private final GlobalErrorResponse error;

    public static <T> GlobalResponse<T> ok(T data) {
        return new GlobalResponse<>(true, data, null);
    }

    public static GlobalResponse<Void> ok() {
        return new GlobalResponse<>(true, null, null);
    }

    public static <T> GlobalResponse<T> error(ErrorCode errorCode) {
        return new GlobalResponse<>(false, null, GlobalErrorResponse.from(errorCode));
    }

    public static <T> GlobalResponse<T> error(GlobalException e) {
        return error(e.getErrorCode());
    }
}
