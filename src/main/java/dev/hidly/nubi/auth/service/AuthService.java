package dev.hidly.nubi.auth.service;

import dev.hidly.nubi.auth.data.dto.request.LoginRequestDto;
import dev.hidly.nubi.auth.data.dto.request.RefreshRequestDto;
import dev.hidly.nubi.auth.data.dto.request.SignUpRequestDto;
import dev.hidly.nubi.auth.data.dto.response.TokenResponseDto;
import dev.hidly.nubi.auth.data.exception.EmailDuplicationException;
import dev.hidly.nubi.auth.data.exception.InvalidCredentialsException;
import dev.hidly.nubi.auth.data.exception.LoginAttemptExceededException;
import dev.hidly.nubi.auth.data.exception.MemberNotFoundException;
import dev.hidly.nubi.common.geo.GeoPoints;
import dev.hidly.nubi.common.logging.LogMasking;
import dev.hidly.nubi.common.redis.RedisService;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.AuthenticationToken;
import dev.hidly.nubi.common.security.JwtTokenProvider;
import dev.hidly.nubi.common.security.LoginMember;
import dev.hidly.nubi.common.security.exception.TokenInvalidException;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import dev.hidly.nubi.organization.data.repository.MemberRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private static final String LOGIN_ATTEMPT_PREFIX = "login-fail:";
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCK_DURATION = Duration.ofMinutes(15);

    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisService redisService;
    private volatile String dummyHash;

    @Transactional
    public GlobalResponse<TokenResponseDto> signUp(SignUpRequestDto request) {
        String email = normalizeEmail(request.getEmail());

        if (memberRepository.existsByEmail(email)) {
            log.warn("[signUp] 중복된 이메일로 가입 시도: {}", LogMasking.email(email));
            throw new EmailDuplicationException();
        }

        String address = request.getAddress().trim();
        Organization organization = organizationRepository.save(Organization.builder()
                .name(request.getOrgName().trim())
                .type(request.getType())
                .address(address)
                .district(District.fromAddress(address).orElse(null))
                .phone(trimToNull(request.getPhone()))
                .location(request.hasLocation() ? GeoPoints.of(request.getLatitude(), request.getLongitude()) : null)
                .build());

        Member member;
        try {
            member = memberRepository.saveAndFlush(Member.builder()
                    .organization(organization)
                    .email(email)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .role(MemberRole.ADMIN)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.warn("[signUp] 동시 가입으로 이메일 중복: {}", LogMasking.email(email));
            throw new EmailDuplicationException();
        }
        log.info("[signUp] 기관 가입 완료: organizationId={}, memberId={}", organization.getId(), member.getId());

        return GlobalResponse.ok(issueToken(member));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<TokenResponseDto> login(LoginRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        String attemptKey = LOGIN_ATTEMPT_PREFIX + email;
        if (failedAttempts(attemptKey) >= MAX_LOGIN_ATTEMPTS) {
            log.warn("[login] 로그인 시도 제한: {}", LogMasking.email(email));
            throw new LoginAttemptExceededException();
        }

        Optional<Member> found = memberRepository.findByEmail(email)
                .filter(member -> !member.getOrganization().isDeleted());
        boolean matched = passwordMatches(request.getPassword(), found.map(Member::getPasswordHash).orElse(null));
        if (found.isEmpty() || !matched) {
            redisService.increment(attemptKey, LOGIN_LOCK_DURATION);
            log.warn("[login] 로그인 실패: {}", LogMasking.email(email));
            throw new InvalidCredentialsException();
        }

        redisService.deleteValues(attemptKey);
        return GlobalResponse.ok(issueToken(found.get()));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<TokenResponseDto> refresh(RefreshRequestDto request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            log.warn("[refresh] 유효하지 않은 Refresh Token으로 재발급 시도");
            throw new TokenInvalidException();
        }

        String email = tokenProvider.getUsername(refreshToken);
        if (!tokenProvider.consumeRefreshToken(email, refreshToken)) {
            log.warn("[refresh] 저장된 Refresh Token과 불일치하여 재발급 거부: {}", LogMasking.email(email));
            throw new TokenInvalidException();
        }

        Member member = memberRepository.findByEmail(email)
                .filter(found -> !found.getOrganization().isDeleted())
                .orElseThrow(MemberNotFoundException::new);
        log.info("[refresh] 토큰 재발급: memberId={}", member.getId());

        return GlobalResponse.ok(issueToken(member));
    }

    public GlobalResponse<Void> logout(String bearerToken) {
        String accessToken = tokenProvider.resolveToken(bearerToken);

        if (accessToken == null || !tokenProvider.isSignedAccessToken(accessToken)) {
            log.warn("[logout] 유효하지 않은 Access Token으로 로그아웃 시도");
            throw new TokenInvalidException();
        }

        tokenProvider.invalidateToken(accessToken);
        log.info("[logout] 로그아웃 처리 완료");

        return GlobalResponse.ok();
    }

    private long failedAttempts(String attemptKey) {
        Object value = redisService.getValues(attemptKey);
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private boolean passwordMatches(String rawPassword, String passwordHash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash == null ? dummyHash() : passwordHash) && passwordHash != null;
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("nubi-dummy-password");
        }
        return dummyHash;
    }

    private TokenResponseDto issueToken(Member member) {
        LoginMember loginMember = new LoginMember(
                member.getId(),
                member.getOrganization().getId(),
                member.getEmail(),
                member.getRole().getAuthority());
        AuthenticationToken token = tokenProvider.generateToken(loginMember);
        return TokenResponseDto.of(member, token);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
