package dev.hidly.nubi.auth.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class EmailDuplicationException extends GlobalException {
    public EmailDuplicationException() {
        super(ErrorCode.EMAIL_DUPLICATION);
    }
}
