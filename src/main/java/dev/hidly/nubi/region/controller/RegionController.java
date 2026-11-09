package dev.hidly.nubi.region.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.region.data.dto.response.DistrictDetailResponseDto;
import dev.hidly.nubi.region.data.dto.response.DistrictStatsResponseDto;
import dev.hidly.nubi.region.data.dto.response.MarkersResponseDto;
import dev.hidly.nubi.region.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Tag(name = "지도와 통계", description = "사각지대, 지도 표시점")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {
    private final RegionService regionService;

    @GetMapping("/districts")
    @Operation(summary = "구별 사각지대", description = "구마다 장애인복지시설 수, 등록장애인 수, 1천 명당 시설 수, 그 달에 열리는 프로그램 수(공개 중·마감), "
            + "접근성 편의시설이 확인된 장소의 프로그램 수, 시설 한 곳당 프로그램 수를 줍니다. month는 yyyy-MM이고 없으면 이번 달입니다. "
            + "시설 한 곳당 프로그램 수가 적을수록 지도에서 짙게 칠합니다.")
    public GlobalResponse<DistrictStatsResponseDto> getDistricts(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return regionService.getDistricts(month);
    }

    @GetMapping("/districts/{district}")
    @Operation(summary = "읍면동 상세", description = "구 요약(구별 사각지대와 같은 값)과 그 구의 읍면동별 시설 수, 등록장애인 수, 그 달 프로그램 수를 줍니다. "
            + "district는 CHEOIN, GIHEUNG, SUJI. emds는 참여 기회가 적은 순(프로그램이 적은 곳, 같으면 시설이 많은 곳부터)입니다.")
    public GlobalResponse<DistrictDetailResponseDto> getDistrict(
            @PathVariable District district,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return regionService.getDistrict(district, month);
    }

    @GetMapping("/markers")
    @Operation(summary = "지도 표시점", description = "장애인복지시설(검은 점)과 그 달에 열리는 프로그램(파란 점)의 좌표를 줍니다. "
            + "프로그램의 accessible은 장소에 접근성 편의시설이 확인됐는지입니다. district를 주면 그 구만, month는 yyyy-MM이고 없으면 이번 달입니다. "
            + "공동생활가정·거주시설은 이용자가 사는 집이라 이름 대신 시설 구분을 주고 좌표를 약 100m 단위로 흐립니다.")
    public GlobalResponse<MarkersResponseDto> getMarkers(
            @RequestParam(required = false) District district,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return regionService.getMarkers(district, month);
    }
}
