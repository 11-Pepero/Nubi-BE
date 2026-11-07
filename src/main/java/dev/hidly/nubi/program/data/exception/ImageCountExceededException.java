package dev.hidly.nubi.program.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ImageCountExceededException extends GlobalException {
    public ImageCountExceededException() {
        super(ErrorCode.IMAGE_COUNT_EXCEEDED);
    }
}
