package dev.hidly.nubi.program.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class ProgramAccessDeniedException extends GlobalException {
    public ProgramAccessDeniedException() {
        super(ErrorCode.PROGRAM_ACCESS_DENIED);
    }
}
