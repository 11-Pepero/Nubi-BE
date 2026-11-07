package dev.hidly.nubi.common.file.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ImageSizeExceededException extends GlobalException {
    public ImageSizeExceededException() {
        super(ErrorCode.IMAGE_SIZE_EXCEEDED);
    }
}
