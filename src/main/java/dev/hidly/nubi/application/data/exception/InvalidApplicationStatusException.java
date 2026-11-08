package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class InvalidApplicationStatusException extends GlobalException {
    public InvalidApplicationStatusException() {
        super(ErrorCode.INVALID_STATUS);
    }
}
