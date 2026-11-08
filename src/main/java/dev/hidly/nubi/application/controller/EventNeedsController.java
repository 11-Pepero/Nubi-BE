package dev.hidly.nubi.application.controller;

import dev.hidly.nubi.application.data.dto.response.EventNeedsResponseDto;
import dev.hidly.nubi.application.service.EventApplicationService;
import dev.hidly.nubi.common.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "행사 관리", description = "행사 등록, 수정, 사진, 신청 기관 관리")
@RestController
@RequestMapping("/api/v1/events/{eventId}/needs")
@RequiredArgsConstructor
public class EventNeedsController {
    private final EventApplicationService eventApplicationService;

    @GetMapping
    @Operation(summary = "필요 지원 모아보기", description = "주최 기관만 볼 수 있습니다. 거절·취소를 뺀 신청의 필요 지원을 항목별로 합치고(기관별 내역, 준비 상태), 개별 케어와 오는 방법을 모아 보여 줍니다. 수량이 비어 있는 지원은 그 신청의 이용자 수 전체로 셉니다.")
    public GlobalResponse<EventNeedsResponseDto> getNeeds(@PathVariable Long eventId) {
        return eventApplicationService.getNeeds(eventId);
    }
}
