package dev.hidly.nubi.review.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ReviewImageCountExceededException extends GlobalException {
    public ReviewImageCountExceededException() {
        super(ErrorCode.REVIEW_IMAGE_COUNT_EXCEEDED);
    }
}
