package dev.hidly.nubi.common.security.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class AuthenticationInfoException extends GlobalException {
    public AuthenticationInfoException() {
        super(ErrorCode.UNAUTHORIZED);
    }
}
