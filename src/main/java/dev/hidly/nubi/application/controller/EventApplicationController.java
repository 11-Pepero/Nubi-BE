package dev.hidly.nubi.application.controller;

import dev.hidly.nubi.application.data.dto.request.ApplicationDecisionRequestDto;
import dev.hidly.nubi.application.data.dto.request.MessageRequestDto;
import dev.hidly.nubi.application.data.dto.request.SupportPrepRequestDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationDetailResponseDto;
import dev.hidly.nubi.application.data.dto.response.EventApplicationListResponseDto;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.service.EventApplicationService;
import dev.hidly.nubi.common.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "행사 관리", description = "행사 등록, 수정, 사진, 신청 기관 관리")
@RestController
@RequestMapping("/api/v1/events/{eventId}/applications")
@RequiredArgsConstructor
public class EventApplicationController {
    private final EventApplicationService eventApplicationService;

    @GetMapping
    @Operation(summary = "신청 기관 목록", description = "주최 기관만 볼 수 있습니다. status를 여러 번 보내면 함께 조회합니다(새 신청 = SUBMITTED, 문의 있음 = INQUIRY, 확정 = CONFIRMED). 위쪽 숫자(이용자, 동반 인력, 새 신청)는 거절·취소를 뺀 신청 기준이고, statusCounts는 탭별 건수입니다.")
    public GlobalResponse<EventApplicationListResponseDto> getApplications(
            @PathVariable Long eventId,
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventApplicationService.getApplications(eventId, status, page, size);
    }

    @GetMapping("/{applicationId}")
    @Operation(summary = "신청 내용 상세", description = "담당자, 함께 오는 사람, 신청할 때 복사해 둔 필요 지원과 개별 케어, 주고받은 문의를 보여 줍니다.")
    public GlobalResponse<EventApplicationDetailResponseDto> getApplication(@PathVariable Long eventId,
                                                                            @PathVariable Long applicationId) {
        return eventApplicationService.getApplication(eventId, applicationId);
    }

    @PatchMapping("/{applicationId}/supports/{supportId}")
    @Operation(summary = "지원 준비 체크", description = "신청한 필요 지원 하나의 준비 상태(PENDING 준비 전, READY 준비함, CHECK 확인 필요)와 주최 메모를 바꿉니다. supportId는 신청 상세의 supports[].applicationSupportId입니다.")
    public GlobalResponse<EventApplicationDetailResponseDto> updateSupportPrep(@PathVariable Long eventId,
                                                                              @PathVariable Long applicationId,
                                                                              @PathVariable Long supportId,
                                                                              @Valid @RequestBody SupportPrepRequestDto request) {
        return eventApplicationService.updateSupportPrep(eventId, applicationId, supportId, request);
    }

    @PostMapping("/{applicationId}/messages")
    @Operation(summary = "답장 보내기", description = "신청 기관의 문의에 주최 기관이 답장합니다. 취소된 신청에는 보낼 수 없습니다.")
    public GlobalResponse<EventApplicationDetailResponseDto> reply(@PathVariable Long eventId,
                                                                   @PathVariable Long applicationId,
                                                                   @Valid @RequestBody MessageRequestDto request) {
        return eventApplicationService.reply(eventId, applicationId, request);
    }

    @PatchMapping("/{applicationId}/status")
    @Operation(summary = "신청 확정, 대기, 거절", description = "status는 CONFIRMED, WAITING, REJECTED 중 하나. 접수 기다림·문의 있음·대기·확정 상태의 신청만 바꿀 수 있고, 같은 상태로는 바꿀 수 없습니다. reason을 보내면 주최 기관 메시지로 남깁니다.")
    public GlobalResponse<EventApplicationDetailResponseDto> decide(@PathVariable Long eventId,
                                                                    @PathVariable Long applicationId,
                                                                    @Valid @RequestBody ApplicationDecisionRequestDto request) {
        return eventApplicationService.decide(eventId, applicationId, request);
    }
}
