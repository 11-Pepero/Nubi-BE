package dev.hidly.nubi.auth.service;

import dev.hidly.nubi.auth.data.dto.request.LoginRequestDto;
import dev.hidly.nubi.auth.data.dto.request.RefreshRequestDto;
import dev.hidly.nubi.auth.data.dto.request.SignUpRequestDto;
import dev.hidly.nubi.auth.data.dto.response.TokenResponseDto;
import dev.hidly.nubi.auth.data.exception.EmailDuplicationException;
import dev.hidly.nubi.auth.data.exception.InvalidCredentialsException;
import dev.hidly.nubi.auth.data.exception.MemberNotFoundException;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.AuthenticationToken;
import dev.hidly.nubi.common.security.JwtTokenProvider;
import dev.hidly.nubi.common.security.LoginMember;
import dev.hidly.nubi.common.security.exception.TokenInvalidException;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import dev.hidly.nubi.organization.data.repository.MemberRepository;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final OrganizationRepository organizationRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public GlobalResponse<TokenResponseDto> signUp(SignUpRequestDto request) {
        String email = normalizeEmail(request.getEmail());

        if (memberRepository.existsByEmail(email)) {
            log.warn("[signUp] 중복된 이메일로 가입 시도: {}", email);
            throw new EmailDuplicationException();
        }

        Organization organization = organizationRepository.save(Organization.builder()
                .name(request.getOrgName().trim())
                .type(request.getType())
                .address(request.getAddress().trim())
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
            log.warn("[signUp] 동시 가입으로 이메일 중복: {}", email);
            throw new EmailDuplicationException();
        }
        log.info("[signUp] 기관 가입 완료: organizationId={}, memberId={}", organization.getId(), member.getId());

        return GlobalResponse.ok(issueToken(member));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<TokenResponseDto> login(LoginRequestDto request) {
        String email = normalizeEmail(request.getEmail());

        Member member = memberRepository.findByEmail(email)
                .filter(found -> !found.getOrganization().isDeleted())
                .filter(found -> passwordEncoder.matches(request.getPassword(), found.getPasswordHash()))
                .orElseThrow(() -> {
                    log.warn("[login] 로그인 실패: {}", email);
                    return new InvalidCredentialsException();
                });

        return GlobalResponse.ok(issueToken(member));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<TokenResponseDto> refresh(RefreshRequestDto request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            log.warn("[refresh] 유효하지 않은 Refresh Token으로 재발급 시도");
            throw new TokenInvalidException();
        }

        String email = tokenProvider.getUsername(refreshToken);
        if (!tokenProvider.isStoredRefreshToken(email, refreshToken)) {
            log.warn("[refresh] 저장된 Refresh Token과 불일치하여 재발급 거부: {}", email);
            throw new TokenInvalidException();
        }

        Member member = memberRepository.findByEmail(email)
                .filter(found -> !found.getOrganization().isDeleted())
                .orElseThrow(MemberNotFoundException::new);
        log.info("[refresh] 토큰 재발급: {}", email);

        return GlobalResponse.ok(issueToken(member));
    }

    public GlobalResponse<Void> logout(String bearerToken) {
        String accessToken = tokenProvider.resolveToken(bearerToken);

        if (accessToken == null || !tokenProvider.validateAccessToken(accessToken)) {
            log.warn("[logout] 유효하지 않은 Access Token으로 로그아웃 시도");
            throw new TokenInvalidException();
        }

        tokenProvider.invalidateToken(accessToken);
        log.info("[logout] 로그아웃 처리 완료");

        return GlobalResponse.ok();
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

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
