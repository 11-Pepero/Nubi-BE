package dev.hidly.nubi.program.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ImageOrderInvalidException extends GlobalException {
    public ImageOrderInvalidException() {
        super(ErrorCode.IMAGE_ORDER_INVALID);
    }
}
