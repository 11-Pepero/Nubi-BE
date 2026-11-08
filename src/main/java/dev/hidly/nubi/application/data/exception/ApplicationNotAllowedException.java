package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ApplicationNotAllowedException extends GlobalException {
    public ApplicationNotAllowedException() {
        super(ErrorCode.APPLICATION_NOT_ALLOWED);
    }
}
