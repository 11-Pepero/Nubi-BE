package dev.hidly.nubi.organization.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class OrganizationAccessDeniedException extends GlobalException {
    public OrganizationAccessDeniedException() {
        super(ErrorCode.FORBIDDEN);
    }
}
