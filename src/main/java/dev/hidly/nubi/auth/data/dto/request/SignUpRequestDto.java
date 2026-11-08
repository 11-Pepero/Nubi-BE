package dev.hidly.nubi.auth.data.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.hidly.nubi.organization.data.enums.OrganizationType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;

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

    @Size(max = 20)
    private String phone;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;

    @JsonIgnore
    @AssertTrue(message = "latitude와 longitude는 함께 보내야 합니다.")
    public boolean isLocationPaired() {
        return (latitude == null) == (longitude == null);
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    @JsonIgnore
    @AssertTrue(message = "비밀번호는 72바이트를 넘을 수 없습니다.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
