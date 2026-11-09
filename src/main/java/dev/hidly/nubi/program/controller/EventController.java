package dev.hidly.nubi.program.controller;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.program.data.dto.request.EventCreateRequestDto;
import dev.hidly.nubi.program.data.dto.response.EventDashboardResponseDto;
import dev.hidly.nubi.program.data.dto.response.EventSummaryResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramDetailResponseDto;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.service.EventService;
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
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    @PostMapping
    @Operation(summary = "행사 등록", description = "로그인 기관이 주최하는 행사를 만듭니다. status는 DRAFT(임시 저장) 또는 OPEN(공개하기), 신청 방법은 DIRECT(앱에서 바로 신청)로 고정됩니다. conditions는 주최가 확인한 참여 조건(source=HOST)으로 저장합니다.")
    public GlobalResponse<ProgramDetailResponseDto> create(@Valid @RequestBody EventCreateRequestDto request) {
        return eventService.create(request);
    }

    @PutMapping("/{eventId}")
    @Operation(summary = "행사 수정", description = "요청은 행사 등록과 같습니다. 주최 기관만 고칠 수 있고, 끝난 행사는 고칠 수 없습니다. 공개한 행사를 임시 저장으로 되돌릴 수 없고, 정원을 이미 신청한 인원보다 적게 줄이면 409입니다. 주최가 넣은 조건(HOST)은 요청 내용으로 바뀝니다.")
    public GlobalResponse<ProgramDetailResponseDto> update(@PathVariable Long eventId,
                                                           @Valid @RequestBody EventCreateRequestDto request) {
        return eventService.update(eventId, request);
    }

    @PatchMapping("/{eventId}/close")
    @Operation(summary = "신청 마감", description = "공개 중(OPEN)인 행사를 마감(CLOSED)합니다. 마감하면 더 이상 바로 신청을 받지 않습니다.")
    public GlobalResponse<ProgramDetailResponseDto> close(@PathVariable Long eventId) {
        return eventService.close(eventId);
    }

    @GetMapping("/summary")
    @Operation(summary = "행사 관리 요약", description = "진행 중(공개 중+마감)·지난·임시 저장 행사 수, 아직 정하지 않은 새 신청 수, 이번 달에 시작하는 행사의 확정 인원(이용자, 동반 인력)입니다. 받은 후기 수는 후기 기능이 생기면 추가합니다.")
    public GlobalResponse<EventDashboardResponseDto> getDashboard() {
        return eventService.getDashboard();
    }

    @GetMapping
    @Operation(summary = "내 행사 목록", description = "로그인 기관이 주최한 행사입니다. status를 여러 번 보내면 함께 조회합니다(진행 중 = OPEN, CLOSED / 지난 행사 = ENDED / 임시 저장 = DRAFT). 신청 기관 수, 참여 인원(이용자), 새 신청 수는 거절·취소를 뺀 신청 기준입니다.")
    public GlobalResponse<PageResponse<EventSummaryResponseDto>> getMyEvents(
            @RequestParam(required = false) List<ProgramStatus> status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return eventService.getMyEvents(status, page, size);
    }
}
