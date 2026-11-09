package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ApplicationSupportNotFoundException extends GlobalException {
    public ApplicationSupportNotFoundException() {
        super(ErrorCode.NOT_FOUND);
    }
}
