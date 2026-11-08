package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ApplicationAccessDeniedException extends GlobalException {
    public ApplicationAccessDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
