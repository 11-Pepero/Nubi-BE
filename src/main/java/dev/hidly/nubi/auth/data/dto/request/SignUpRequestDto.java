package dev.hidly.nubi.auth.data.dto.request;

import dev.hidly.nubi.organization.data.enums.OrganizationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDto {
    @NotBlank
    @Size(max = 100)
    private String orgName;

    @NotNull
    private OrganizationType type;

    @NotBlank
    @Size(max = 200)
    private String address;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
