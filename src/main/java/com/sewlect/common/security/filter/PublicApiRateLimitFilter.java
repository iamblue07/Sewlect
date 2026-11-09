package com.sewlect.common.security.filter;

import com.sewlect.common.ratelimit.RateLimiter;
import com.sewlect.common.security.properties.PublicApiRateLimitProperties;
import com.sewlect.common.security.support.RateLimitRejectionWriter;
import com.sewlect.common.security.support.RateLimitSubjectHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    public static final String PUBLIC_PATH_PREFIX = "/api/v1/public/";
    public static final String IP_OPERATION_KEY = "public-ip";

    private static final String REJECTION_MESSAGE = "Too many requests - try again later";

    private final RateLimiter rateLimiter;
    private final RateLimitSubjectHasher subjectHasher;
    private final PublicApiRateLimitProperties properties;
    private final RateLimitRejectionWriter rejectionWriter;

    public PublicApiRateLimitFilter(RateLimiter rateLimiter,
                                    RateLimitSubjectHasher subjectHasher,
                                    PublicApiRateLimitProperties properties,
                                    RateLimitRejectionWriter rejectionWriter) {
        this.rateLimiter = rateLimiter;
        this.subjectHasher = subjectHasher;
        this.properties = properties;
        this.rejectionWriter = rejectionWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(PUBLIC_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimiter.tryConsume(subjectHasher.hash(request.getRemoteAddr()), IP_OPERATION_KEY,
                properties.perIpLimit(), properties.window())) {
            rejectionWriter.write(request, response, REJECTION_MESSAGE);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
