package dev.hidly.nubi.program.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ProgramNotFoundException extends GlobalException {
    public ProgramNotFoundException() {
        super(ErrorCode.PROGRAM_NOT_FOUND);
    }
}
