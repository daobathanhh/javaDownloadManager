package com.java_download_manager.jdm.controllers.http;

import com.java_download_manager.jdm.repository.SessionRepository;
import com.java_download_manager.jdm.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(1)
@RequiredArgsConstructor
public class HttpAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_ACCOUNT_ID = "jdm.accountId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final SessionRepository sessionRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only protect /tasks - leave /login, /signup, /refresh public
        return !path.startsWith("/tasks");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long accountId = resolveAccountId(request);
        if (accountId != null) {
            request.setAttribute(ATTR_ACCOUNT_ID, accountId);
        }
        filterChain.doFilter(request, response);
    }

    private Long resolveAccountId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = auth.substring(BEARER_PREFIX.length()).trim();
        Optional<JwtTokenService.JwtClaims> claims = jwtTokenService.parseAndVerify(token);
        if (claims.isEmpty() || !"access".equals(claims.get().type())) {
            return null;
        }
        String jti = claims.get().jti();
        if (sessionRepository.findByAccessTokenJtiAndRevokedAtIsNull(jti).isEmpty()) {
            return null;
        }
        return claims.get().accountId();
    }

    public static Long getAccountId(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_ACCOUNT_ID);
        return attr instanceof Long ? (Long) attr : null;
    }
}
