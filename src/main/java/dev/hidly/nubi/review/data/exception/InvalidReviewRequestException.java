package dev.hidly.nubi.review.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class InvalidReviewRequestException extends GlobalException {
    public InvalidReviewRequestException() {
        super(ErrorCode.INVALID_REQUEST);
    }
}
