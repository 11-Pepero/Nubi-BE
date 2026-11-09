package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.program.data.dto.response.VenueFacilitiesResponseDto;
import dev.hidly.nubi.program.data.dto.response.VenueResponseDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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

    @GetMapping
    @Operation(summary = "장소 검색", description = "행사 등록에서 장소를 고를 때 씁니다. keyword가 이름이나 주소에 들어간 장소를 이름순으로 찾고, 없으면 전체를 줍니다. "
            + "고른 장소의 venueId를 행사 등록에 넣습니다. page는 0부터, size 기본 20.")
    public GlobalResponse<PageResponse<VenueResponseDto>> search(
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return venueService.search(keyword, page, size);
    }

    @GetMapping("/{venueId}/facilities")
    @Operation(summary = "장소 편의시설", description = "공공데이터, 주최, 후기 출처를 합친 편의시설 정보. 출처마다 다르면 가장 최근에 확인된 값을 씁니다.")
    public GlobalResponse<VenueFacilitiesResponseDto> getFacilities(@PathVariable Long venueId) {
        return venueService.getFacilities(venueId);
    }
}
