package dev.hidly.nubi.review.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ReviewNotAllowedException extends GlobalException {
    public ReviewNotAllowedException() {
        super(ErrorCode.REVIEW_NOT_ALLOWED);
    }
}
