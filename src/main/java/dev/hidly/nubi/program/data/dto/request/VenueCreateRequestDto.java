package dev.hidly.nubi.program.data.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카카오 장소 검색에서 고른 장소를 우리 장소로 등록한다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueCreateRequestDto {
    @NotBlank
    @Size(max = 100)
    private String name;

    // 도로명 주소(없으면 지번 주소)
    @NotBlank
    @Size(max = 200)
    private String address;

    // 우리나라 안의 좌표만 받는다
    @NotNull
    @DecimalMin("33.0")
    @DecimalMax("39.0")
    private Double latitude;

    @NotNull
    @DecimalMin("124.0")
    @DecimalMax("132.0")
    private Double longitude;
}
