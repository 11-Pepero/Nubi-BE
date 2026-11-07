package dev.hidly.nubi.support.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class GroupNotFoundException extends GlobalException {
    public GroupNotFoundException() {
        super(ErrorCode.GROUP_NOT_FOUND);
    }
}
