package dev.hidly.nubi.organization.data.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberRole {
    ADMIN("ROLE_ADMIN"),
    STAFF("ROLE_STAFF");

    private final String authority;
}
