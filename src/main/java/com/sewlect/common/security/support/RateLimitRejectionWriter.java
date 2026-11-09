package com.sewlect.common.security.support;

import com.sewlect.common.exception.dto.ErrorResponse;
import com.sewlect.common.exception.support.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
public class RateLimitRejectionWriter {

    private final ErrorResponseFactory errorResponseFactory;
    private final JsonMapper jsonMapper;

    public RateLimitRejectionWriter(ErrorResponseFactory errorResponseFactory, JsonMapper jsonMapper) {
        this.errorResponseFactory = errorResponseFactory;
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        ErrorResponse body = errorResponseFactory.build(HttpStatus.TOO_MANY_REQUESTS, message, request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
