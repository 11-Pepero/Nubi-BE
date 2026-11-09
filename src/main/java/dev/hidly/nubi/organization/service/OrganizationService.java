package dev.hidly.nubi.organization.service;

import dev.hidly.nubi.auth.data.exception.MemberNotFoundException;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.dto.response.MyOrganizationResponseDto;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.repository.MemberRepository;
import dev.hidly.nubi.organization.data.dto.request.MyOrganizationUpdateRequestDto;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import dev.hidly.nubi.organization.data.exception.OrganizationAccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<MyOrganizationResponseDto> getMyOrganization() {
        Member member = memberRepository.findById(SecurityUtil.getMemberId())
                .orElseThrow(MemberNotFoundException::new);
        return GlobalResponse.ok(MyOrganizationResponseDto.of(member.getOrganization(), member));
    }

    // 내 기관 수정 (API-5): 기관 관리자(ADMIN)만
    @Transactional
    public GlobalResponse<MyOrganizationResponseDto> updateMyOrganization(MyOrganizationUpdateRequestDto request) {
        Member member = memberRepository.findById(SecurityUtil.getMemberId())
                .orElseThrow(MemberNotFoundException::new);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new OrganizationAccessDeniedException();
        }
        Organization organization = member.getOrganization();
        organization.update(trimToNull(request.getName()), trimToNull(request.getAddress()), trimToNull(request.getPhone()));
        log.info("[updateMyOrganization] 기관 수정: organizationId={}", organization.getId());
        return GlobalResponse.ok(MyOrganizationResponseDto.of(organization, member));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
