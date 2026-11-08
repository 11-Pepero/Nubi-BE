package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ApplicationDuplicatedException extends GlobalException {
    public ApplicationDuplicatedException() {
        super(ErrorCode.APPLICATION_DUPLICATED);
    }
}
