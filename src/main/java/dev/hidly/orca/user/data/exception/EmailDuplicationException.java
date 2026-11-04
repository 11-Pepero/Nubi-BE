package dev.hidly.orca.user.data.exception;

import dev.hidly.orca.common.response.ErrorCode;
import dev.hidly.orca.common.response.GlobalException;

public class EmailDuplicationException extends GlobalException {
    public EmailDuplicationException() {
        super(ErrorCode.EMAIL_DUPLICATION);
    }
}
