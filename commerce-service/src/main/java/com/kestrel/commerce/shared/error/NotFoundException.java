package com.kestrel.commerce.shared.error;

import java.util.Map;

public class NotFoundException extends DomainException {

    public NotFoundException(ErrorCode errorCode, String resourceName, Object id) {
        super(errorCode, resourceName + " " + id + " was not found", Map.of("resourceId", String.valueOf(id)));
    }
}
