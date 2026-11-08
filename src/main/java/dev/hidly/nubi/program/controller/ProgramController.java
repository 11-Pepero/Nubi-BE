package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.dto.request.ProgramSearchCondition;
import dev.hidly.nubi.program.data.dto.response.ProgramDetailResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramSummaryResponseDto;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.service.ProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@Tag(name = "프로그램", description = "프로그램 탐색, 상세, 참여 판정, 장소 편의시설")
@RestController
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
public class ProgramController {
    private final ProgramService programService;

    @GetMapping
    @Operation(summary = "프로그램 탐색", description = "공개 중이거나 마감된 프로그램을 검색합니다. month는 yyyy-MM, page는 0부터, size 기본 20.")
    public GlobalResponse<PageResponse<ProgramSummaryResponseDto>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProgramCategory category,
            @RequestParam(required = false) District district,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) ApplyMethod applyMethod,
            @RequestParam(defaultValue = "false") boolean accessibleOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ProgramSearchCondition condition = new ProgramSearchCondition(keyword, category, district, month, applyMethod, accessibleOnly);
        return programService.search(condition, page, size);
    }

    @GetMapping("/{programId}")
    @Operation(summary = "프로그램 상세", description = "사진, 장소, 편의시설. groupId를 주면 로그인한 기관의 그룹 기준 참여 판정을 함께 줍니다. 임시 저장 행사는 주최 기관만 볼 수 있습니다.")
    public GlobalResponse<ProgramDetailResponseDto> getDetail(@PathVariable Long programId,
                                                              @RequestParam(required = false) Long groupId) {
        return programService.getDetail(programId, groupId);
    }
}
