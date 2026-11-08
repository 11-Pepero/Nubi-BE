package dev.hidly.nubi.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String NOTIFICATION_STREAM_PATH = "/api/v1/notifications/stream";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws IOException, ServletException {
        String accessToken = jwtTokenProvider.resolveToken(request.getHeader(JwtTokenProvider.AUTHORIZATION_HEADER));
        // 브라우저 EventSource는 헤더를 못 붙여서 알림 구독 주소에서만 쿼리 토큰을 받는다
        if (accessToken == null && NOTIFICATION_STREAM_PATH.equals(request.getRequestURI())) {
            accessToken = request.getParameter("token");
        }

        if (accessToken != null && jwtTokenProvider.validateAccessToken(accessToken)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }
}
