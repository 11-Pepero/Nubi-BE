package dev.hidly.nubi.review.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ReviewReplyDuplicatedException extends GlobalException {
    public ReviewReplyDuplicatedException() {
        super(ErrorCode.REVIEW_REPLY_DUPLICATED);
    }
}
