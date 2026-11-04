package dev.hidly.orca.user.data.exception;

import dev.hidly.orca.common.response.ErrorCode;
import dev.hidly.orca.common.response.GlobalException;

public class UserNotFoundException extends GlobalException {
    public UserNotFoundException() {
        super(ErrorCode.USER_DATA_NOT_FOUND);
    }
}