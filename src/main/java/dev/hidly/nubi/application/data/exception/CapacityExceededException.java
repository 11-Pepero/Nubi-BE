package dev.hidly.nubi.application.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class CapacityExceededException extends GlobalException {
    public CapacityExceededException() {
        super(ErrorCode.CAPACITY_EXCEEDED);
    }
}
