package dev.hidly.nubi.organization.data.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 내 기관 수정(API-5): 보낸 값만 바꾼다. 빈 문자열은 받지 않는다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyOrganizationUpdateRequestDto {
    @Size(min = 1, max = 100)
    @Pattern(regexp = ".*\\S.*")
    private String name;

    @Size(min = 1, max = 200)
    @Pattern(regexp = ".*\\S.*")
    private String address;

    @Size(max = 20)
    private String phone;
}
