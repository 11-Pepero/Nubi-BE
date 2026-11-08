package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.program.data.dto.response.EligibilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.EligibleProgramResponseDto;
import dev.hidly.nubi.program.data.enums.EligibilityResult;
import dev.hidly.nubi.program.service.EligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@Tag(name = "프로그램", description = "프로그램 탐색, 상세, 참여 판정, 장소 편의시설")
@RestController
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
public class EligibilityController {
    private final EligibilityService eligibilityService;

    @GetMapping("/eligible")
    @Operation(summary = "참여 가능 프로그램", description = "공개 중인 프로그램을 그룹 기준으로 판정한 목록. 참여 가능, 확인 필요, 어려움 순. careIds를 빼면 그룹의 판정 반영 개별 케어를 씁니다.")
    public GlobalResponse<PageResponse<EligibleProgramResponseDto>> getEligiblePrograms(
            @RequestParam Long groupId,
            @RequestParam(required = false) List<Long> careIds,
            @RequestParam(required = false) EligibilityResult result,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eligibilityService.getEligiblePrograms(groupId, careIds, result, month, page, size);
    }

    @GetMapping("/{programId}/eligibility")
    @Operation(summary = "참여 판정 근거", description = "그룹 공통 지원과 개별 케어 항목별 판정")
    public GlobalResponse<EligibilityResponseDto> getEligibility(@PathVariable Long programId,
                                                                @RequestParam Long groupId,
                                                                @RequestParam(required = false) List<Long> careIds) {
        return eligibilityService.getEligibility(programId, groupId, careIds);
    }
}
