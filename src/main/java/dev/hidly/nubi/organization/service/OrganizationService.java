package dev.hidly.nubi.organization.service;

import dev.hidly.nubi.auth.data.exception.MemberNotFoundException;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.dto.response.MyOrganizationResponseDto;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
