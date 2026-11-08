package dev.hidly.nubi.organization.data.dto.response;

import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import dev.hidly.nubi.organization.data.enums.OrganizationType;

// 내 기관 조회(API-4): 사이드바 아래 기관 이름과 로그인한 담당자
public record MyOrganizationResponseDto(
        Long organizationId,
        String name,
        OrganizationType type,
        District district,
        String address,
        String phone,
        MemberInfo member
) {
    public record MemberInfo(Long memberId, String name, String email, MemberRole role, String phone) {
        static MemberInfo from(Member member) {
            return new MemberInfo(member.getId(), member.getName(), member.getEmail(), member.getRole(), member.getPhone());
        }
    }

    public static MyOrganizationResponseDto of(Organization organization, Member member) {
        return new MyOrganizationResponseDto(
                organization.getId(),
                organization.getName(),
                organization.getType(),
                organization.getDistrict(),
                organization.getAddress(),
                organization.getPhone(),
                MemberInfo.from(member));
    }
}
