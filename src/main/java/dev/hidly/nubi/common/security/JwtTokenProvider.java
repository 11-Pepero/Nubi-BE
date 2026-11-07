package dev.hidly.nubi.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import dev.hidly.nubi.common.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtTokenProvider {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
    private static final String BLACKLIST_VALUE = "logout";

    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String MEMBER_ID_CLAIM = "memberId";
    private static final String ORGANIZATION_ID_CLAIM = "organizationId";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey key;
    private final RedisService redisService;

    // AT 만료 시간
    @Value("${jwt.access-token.expire-time}")
    private long ACCESS_TOKEN_EXPIRE_TIME;

    // RT 만료 시간
    @Value("${jwt.refresh-token.expire-time}")
    private long REFRESH_TOKEN_EXPIRE_TIME;

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey,
                            RedisService redisService) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.redisService = redisService;
    }

    private JwtBuilder baseToken(String email, String tokenType, Date expireDate) {
        log.info("[createToken] 새 {} JWT 발급 됨: {}", tokenType, email);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .expiration(expireDate)
                .signWith(key, Jwts.SIG.HS256);
    }

    public AuthenticationToken generateToken(LoginMember loginMember) {
        String username = loginMember.email();

        log.info("[generateToken] 새 JWT 발급 시도: {}", username);

        long now = (new Date()).getTime();

        String accessToken = baseToken(username, ACCESS_TOKEN_TYPE, new Date(now + ACCESS_TOKEN_EXPIRE_TIME))
                .claim(MEMBER_ID_CLAIM, loginMember.memberId())
                .claim(ORGANIZATION_ID_CLAIM, loginMember.organizationId())
                .claim(AUTHORITIES_CLAIM, loginMember.role())
                .compact();
        String refreshToken = baseToken(username, REFRESH_TOKEN_TYPE, new Date(now + REFRESH_TOKEN_EXPIRE_TIME))
                .compact();

        log.info("[generateToken] 발급된 Refresh Token이 Redis에 저장 됨");
        redisService.setValues(REFRESH_KEY_PREFIX + username, refreshToken,
                Duration.ofMillis(REFRESH_TOKEN_EXPIRE_TIME));

        return AuthenticationToken.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public String resolveToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    public void invalidateToken(String accessToken) {
        String username = parseClaims(accessToken).getSubject();

        Object storedRefreshToken = redisService.getValues(REFRESH_KEY_PREFIX + username);
        if (storedRefreshToken != null) {
            blacklistToken(storedRefreshToken.toString(), "Refresh Token", username);
            redisService.deleteValues(REFRESH_KEY_PREFIX + username);
            log.info("[invalidateToken] 저장된 Refresh Token이 Redis에서 삭제 됨: {}", username);
        }

        blacklistToken(accessToken, "Access Token", username);
    }

    private void blacklistToken(String token, String tokenName, String username) {
        long remainingTime = parseClaims(token).getExpiration().getTime() - System.currentTimeMillis();

        if (remainingTime <= 0) {
            log.info("[blacklistToken] 이미 만료된 {}이라 블랙리스트 등록 생략: {}", tokenName, username);
            return;
        }

        redisService.setValues(BLACKLIST_KEY_PREFIX + token, BLACKLIST_VALUE, Duration.ofMillis(remainingTime));
        log.info("[blacklistToken] {}이 블랙리스트에 등록 됨: {} (남은 시간: {}ms)", tokenName, username, remainingTime);
    }

    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isStoredRefreshToken(String username, String refreshToken) {
        Object storedRefreshToken = redisService.getValues(REFRESH_KEY_PREFIX + username);
        return storedRefreshToken != null && storedRefreshToken.toString().equals(refreshToken);
    }

    public boolean isBlacklisted(String token) {
        return redisService.hasKey(BLACKLIST_KEY_PREFIX + token);
    }

    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);
        Object authClaim = claims.get(AUTHORITIES_CLAIM);

        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM)) || authClaim == null || authClaim.toString().isEmpty()
                || claims.get(MEMBER_ID_CLAIM) == null || claims.get(ORGANIZATION_ID_CLAIM) == null) {
            throw new IllegalArgumentException("Access Token이 아닌 JWT로 인증 정보를 만들 수 없습니다.");
        }

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(authClaim.toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        LoginMember principal = new LoginMember(
                claims.get(MEMBER_ID_CLAIM, Long.class),
                claims.get(ORGANIZATION_ID_CLAIM, Long.class),
                claims.getSubject(),
                authClaim.toString());
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN_TYPE);
    }

    private boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(TOKEN_TYPE_CLAIM))) {
                log.warn("[validateToken] 용도가 다른 JWT 요청 (기대: {}, 실제: {})",
                        expectedType, claims.get(TOKEN_TYPE_CLAIM));
                return false;
            }

            if (ACCESS_TOKEN_TYPE.equals(expectedType)
                    && (claims.get(MEMBER_ID_CLAIM) == null || claims.get(ORGANIZATION_ID_CLAIM) == null)) {
                log.warn("[validateToken] 담당자 정보가 없는 Access Token 요청");
                return false;
            }

            if (isBlacklisted(token)) {
                log.warn("[validateToken] 로그아웃 처리된 JWT 인증 요청");
                return false;
            }

            return true;
        } catch (SignatureException | MalformedJwtException e) {
            log.warn("[validateToken] 유효하지 않은 JWT 서명 요청: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("[validateToken] 만료된 JWT 인증 요청: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("[validateToken] 지원되지 않는 JWT 인증 요청: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("[validateToken] JWT가 제출되지 않음: {}", e.getMessage());
        }
        return false;
    }
}
