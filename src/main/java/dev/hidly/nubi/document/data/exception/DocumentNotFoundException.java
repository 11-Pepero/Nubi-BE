package dev.hidly.nubi.document.data.exception;

import dev.hidly.nubi.common.response.ErrorCode;
import dev.hidly.nubi.common.response.GlobalException;

public class DocumentNotFoundException extends GlobalException {
    public DocumentNotFoundException() {
        super(ErrorCode.DOCUMENT_NOT_FOUND);
    }
}
