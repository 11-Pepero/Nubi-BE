package dev.hidly.nubi.program.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class InvalidEventRequestException extends GlobalException {
    public InvalidEventRequestException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
