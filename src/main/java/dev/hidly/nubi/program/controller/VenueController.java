package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.program.data.dto.request.VenueCreateRequestDto;
import dev.hidly.nubi.program.data.dto.response.PlaceCandidateResponseDto;
import dev.hidly.nubi.program.data.dto.response.VenueFacilitiesResponseDto;
import dev.hidly.nubi.program.data.dto.response.VenueResponseDto;
import dev.hidly.nubi.program.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/places")
    @Operation(summary = "카카오 장소 검색", description = "행사 등록에서 우리 장소 목록에 없는 곳을 찾을 때 씁니다. 카카오 로컬 키워드 검색으로 용인시 범위의 장소를 "
            + "정확도 순으로 최대 15곳 줍니다(이름, 분류, 도로명 주소, 지번 주소, 전화, 좌표). 이미 우리 장소로 등록된 곳이면 venueId가 있고, "
            + "없으면 장소 등록(POST /venues)으로 등록한 뒤 그 venueId를 행사 등록에 넣습니다. 카카오 키가 없으면 빈 목록입니다.")
    public GlobalResponse<List<PlaceCandidateResponseDto>> searchPlaces(@RequestParam @NotBlank @Size(max = 50) String query) {
        return venueService.searchPlaces(query);
    }

    @PostMapping
    @Operation(summary = "장소 등록", description = "카카오 장소 검색에서 고른 장소를 우리 장소로 등록합니다. 이름과 주소가 같은 장소가 있으면(또는 이름이 같고 주소가 비어 있으면) "
            + "그 장소에 주소·좌표를 채워 돌려주고, 없으면 새로 만듭니다. 주소로 구(처인·기흥·수지)를, 좌표로 행정동을 채우고, "
            + "장애인편의시설·무장애 여행 정보 편의시설은 등록 뒤 잠시 후 채워집니다.")
    public GlobalResponse<VenueResponseDto> register(@Valid @RequestBody VenueCreateRequestDto request) {
        return venueService.register(request);
    }

    @GetMapping("/{venueId}/facilities")
    @Operation(summary = "장소 편의시설", description = "공공데이터, 주최, 후기 출처를 합친 편의시설 정보. 출처마다 다르면 가장 최근에 확인된 값을 씁니다.")
    public GlobalResponse<VenueFacilitiesResponseDto> getFacilities(@PathVariable Long venueId) {
        return venueService.getFacilities(venueId);
    }
}
