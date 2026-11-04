package dev.hidly.orca.common.security.exception;

import dev.hidly.orca.common.response.ErrorCode;
import dev.hidly.orca.common.response.GlobalException;

public class TokenInvalidException extends GlobalException {
    public TokenInvalidException() {
        super(ErrorCode.TOKEN_INVALID);
    }
}
