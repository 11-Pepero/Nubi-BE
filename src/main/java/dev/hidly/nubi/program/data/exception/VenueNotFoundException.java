package dev.hidly.nubi.program.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class VenueNotFoundException extends GlobalException {
    public VenueNotFoundException() {
        super(ErrorCode.VENUE_NOT_FOUND);
    }
}
