package dev.hidly.nubi.support.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.support.data.dto.response.SupportCategoryResponseDto;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import dev.hidly.nubi.support.service.SupportTagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support-tags")
@RequiredArgsConstructor
public class SupportTagController {
    private final SupportTagService supportTagService;

    @GetMapping
    @Operation(summary = "지원 항목 사전", description = "분류별 지원 항목 목록. category를 주면 그 분류만 반환합니다.")
    public GlobalResponse<List<SupportCategoryResponseDto>> getSupportTags(
            @RequestParam(required = false) SupportCategory category) {
        return supportTagService.getSupportTags(category);
    }
}
