package dev.hidly.orca.user.data.exception;

import dev.hidly.orca.common.response.ErrorCode;
import dev.hidly.orca.common.response.GlobalException;

public class PasswordInvalidException extends GlobalException {
    public PasswordInvalidException() {
        super(ErrorCode.PASSWORD_INVALID);
    }
}