package dev.hidly.nubi.support.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class GroupAccessDeniedException extends GlobalException {
    public GroupAccessDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
