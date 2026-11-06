package dev.hidly.nubi.auth.data.dto.response;

import dev.hidly.nubi.common.security.AuthenticationToken;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenResponseDto {
    private final String accessToken;
    private final String refreshToken;
    private final Long memberId;
    private final String email;
    private final MemberRole role;
    private final Long organizationId;
    private final String organizationName;

    public static TokenResponseDto of(Member member, AuthenticationToken token) {
        return TokenResponseDto.builder()
                .accessToken(token.getAccessToken())
                .refreshToken(token.getRefreshToken())
                .memberId(member.getId())
                .email(member.getEmail())
                .role(member.getRole())
                .organizationId(member.getOrganization().getId())
                .organizationName(member.getOrganization().getName())
                .build();
    }
}
