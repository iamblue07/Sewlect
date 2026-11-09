package com.sewlect.common.security.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.common.security.properties.AuthRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import com.sewlect.common.security.support.RateLimitRejectionWriter;
import com.sewlect.common.security.support.RateLimitRejectionWriter;
import com.sewlect.common.security.support.RateLimitSubjectHasher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    public static final String IP_OPERATION_KEY = "auth-ip";
    public static final String EMAIL_OPERATION_KEY = "auth-email";

    public static final String REGISTER_PATH = "/api/v1/auth/register";
    public static final String LOGIN_PATH = "/api/v1/auth/login";
    public static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(REGISTER_PATH, LOGIN_PATH, REFRESH_PATH);
    private static final String REJECTION_MESSAGE =
            "Too many authentication attempts - try again later";

    private final RateLimiter rateLimiter;
    private final RateLimitSubjectHasher subjectHasher;
    private final AuthRateLimitProperties properties;
    private final RateLimitRejectionWriter rejectionWriter;
    private final JsonMapper jsonMapper;

    public AuthRateLimitFilter(RateLimiter rateLimiter,
                               RateLimitSubjectHasher subjectHasher,
                               AuthRateLimitProperties properties,
                               RateLimitRejectionWriter rejectionWriter,
                               JsonMapper jsonMapper) {
        this.rateLimiter = rateLimiter;
        this.subjectHasher = subjectHasher;
        this.properties = properties;
        this.rejectionWriter = rejectionWriter;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!RATE_LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimiter.tryConsume(subjectHasher.hash(resolveClientIp(request)), IP_OPERATION_KEY,
                properties.perIpLimit(), properties.window())) {
            reject(request, response);
            return;
        }

        if (!LOGIN_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        String email = extractEmail(body);

        if (email != null && !rateLimiter.tryConsume(subjectHasher.hash(email), EMAIL_OPERATION_KEY,
                properties.perEmailLimit(), properties.window())) {
            reject(request, response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String extractEmail(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        try {
            LoginEmailView view = jsonMapper.readValue(body, LoginEmailView.class);
            if (view == null || !StringUtils.hasText(view.email())) {
                return null;
            }
            return view.email().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        rejectionWriter.write(request, response, REJECTION_MESSAGE);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LoginEmailView(String email) {
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return source.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}