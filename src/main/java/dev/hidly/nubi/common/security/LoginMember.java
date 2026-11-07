package dev.hidly.nubi.common.security;

import java.security.Principal;

public record LoginMember(Long memberId, Long organizationId, String email, String role) implements Principal {
    @Override
    public String getName() {
        return email;
    }
}
