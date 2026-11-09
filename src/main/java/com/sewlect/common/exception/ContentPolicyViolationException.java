package com.sewlect.common.exception;

import org.springframework.http.HttpStatus;

public class ContentPolicyViolationException extends AppException {

    public ContentPolicyViolationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
