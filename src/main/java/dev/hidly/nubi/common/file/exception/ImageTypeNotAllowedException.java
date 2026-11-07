package dev.hidly.nubi.common.file.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ImageTypeNotAllowedException extends GlobalException {
    public ImageTypeNotAllowedException() {
        super(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
    }
}
