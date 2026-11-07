package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.program.data.dto.response.VenueFacilitiesResponseDto;
import dev.hidly.nubi.program.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로그램", description = "프로그램 탐색, 상세, 참여 판정, 장소 편의시설")
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {
    private final VenueService venueService;

    @GetMapping("/{venueId}/facilities")
    @Operation(summary = "장소 편의시설", description = "공공데이터, 주최, 후기 출처를 합친 편의시설 정보. 출처마다 다르면 가장 최근에 확인된 값을 씁니다.")
    public GlobalResponse<VenueFacilitiesResponseDto> getFacilities(@PathVariable Long venueId) {
        return venueService.getFacilities(venueId);
    }
}
