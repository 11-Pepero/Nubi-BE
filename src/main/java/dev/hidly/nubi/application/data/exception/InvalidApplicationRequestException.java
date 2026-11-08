package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class InvalidApplicationRequestException extends GlobalException {
    public InvalidApplicationRequestException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
