package dev.hidly.nubi.support.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class CarePersonNotFoundException extends GlobalException {
    public CarePersonNotFoundException() {
        super(ErrorCode.CARE_PERSON_NOT_FOUND);
    }
}
