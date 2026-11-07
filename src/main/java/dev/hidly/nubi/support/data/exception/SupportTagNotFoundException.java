package dev.hidly.nubi.support.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class SupportTagNotFoundException extends GlobalException {
    public SupportTagNotFoundException() {
        super(ErrorCode.SUPPORT_TAG_NOT_FOUND);
    }
}
