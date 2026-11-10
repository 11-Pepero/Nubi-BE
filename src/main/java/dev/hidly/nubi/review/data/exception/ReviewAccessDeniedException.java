package dev.hidly.nubi.review.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ReviewAccessDeniedException extends GlobalException {
    public ReviewAccessDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
