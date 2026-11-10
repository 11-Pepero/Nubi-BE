package dev.hidly.nubi.review.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ReviewDuplicatedException extends GlobalException {
    public ReviewDuplicatedException() {
        super(ErrorCode.REVIEW_DUPLICATED);
    }
}
