package dev.hidly.nubi.application.data.dto.response;

import dev.hidly.nubi.organization.data.entity.Member;

public record ContactResponseDto(String name, String phone) {
    public static ContactResponseDto from(Member member) {
        return member == null ? null : new ContactResponseDto(member.getName(), member.getPhone());
    }
}
