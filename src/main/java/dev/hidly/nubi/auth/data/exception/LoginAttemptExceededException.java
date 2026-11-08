package dev.hidly.nubi.auth.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class LoginAttemptExceededException extends GlobalException {
    public LoginAttemptExceededException() {
        super(ErrorCode.TOO_MANY_REQUESTS);
    }
}
