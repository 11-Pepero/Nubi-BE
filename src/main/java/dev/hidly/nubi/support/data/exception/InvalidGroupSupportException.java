package dev.hidly.nubi.support.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class InvalidGroupSupportException extends GlobalException {
    public InvalidGroupSupportException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
