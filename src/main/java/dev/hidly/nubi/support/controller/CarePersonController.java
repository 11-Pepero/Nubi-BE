package dev.hidly.nubi.support.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.support.data.dto.request.CarePersonCreateRequestDto;
import dev.hidly.nubi.support.data.dto.request.CarePersonUpdateRequestDto;
import dev.hidly.nubi.support.data.dto.response.CarePersonResponseDto;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import dev.hidly.nubi.support.service.CarePersonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "지원 관리", description = "지원 항목 사전, 공통 지원 그룹, 개별 케어")
@RestController
@RequestMapping("/api/v1/care-persons")
@RequiredArgsConstructor
public class CarePersonController {
    private final CarePersonService carePersonService;

    @GetMapping
    @Operation(summary = "개별 케어 명단", description = "그룹(groupId), 별칭(keyword), 케어 종류(category)로 거를 수 있습니다.")
    public GlobalResponse<List<CarePersonResponseDto>> getCarePersons(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SupportCategory category) {
        return carePersonService.getCarePersons(groupId, keyword, category);
    }

    @PostMapping
    @Operation(summary = "개별 케어 추가", description = "별칭과 케어 항목 등록. 실명과 진단명은 적지 않습니다.")
    public GlobalResponse<CarePersonResponseDto> createCarePerson(@Valid @RequestBody CarePersonCreateRequestDto request) {
        return carePersonService.createCarePerson(request);
    }

    @GetMapping("/{careId}")
    @Operation(summary = "개별 케어 상세", description = "케어 항목과 판정 영향. judgementImpact에 이 사람 때문에 판정이 달라지는 공개 중 프로그램(최대 20개)이 나옵니다.")
    public GlobalResponse<CarePersonResponseDto> getCarePerson(@PathVariable Long careId) {
        return carePersonService.getCarePerson(careId);
    }

    @PutMapping("/{careId}")
    @Operation(summary = "개별 케어 수정", description = "별칭, 케어 항목, 메모, 판정 반영 여부를 요청 내용으로 바꿉니다.")
    public GlobalResponse<CarePersonResponseDto> updateCarePerson(@PathVariable Long careId,
                                                                  @Valid @RequestBody CarePersonUpdateRequestDto request) {
        return carePersonService.updateCarePerson(careId, request);
    }

    @DeleteMapping("/{careId}")
    @Operation(summary = "개별 케어 삭제", description = "소프트 삭제")
    public GlobalResponse<Void> deleteCarePerson(@PathVariable Long careId) {
        return carePersonService.deleteCarePerson(careId);
    }
}
