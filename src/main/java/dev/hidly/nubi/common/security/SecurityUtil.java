package dev.hidly.nubi.common.security;

import dev.hidly.nubi.common.security.exception.AuthenticationInfoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityUtil {
    public static LoginMember getLoginMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginMember loginMember)) {
            throw new AuthenticationInfoException();
        }
        return loginMember;
    }

    public static Optional<Long> findOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginMember loginMember) {
            return Optional.of(loginMember.organizationId());
        }
        return Optional.empty();
    }

    public static String getUsername() {
        return getLoginMember().email();
    }

    public static Long getMemberId() {
        return getLoginMember().memberId();
    }

    public static Long getOrganizationId() {
        return getLoginMember().organizationId();
    }
}
