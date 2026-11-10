package dev.hidly.nubi.promotion.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.program.data.dto.response.FacilityResponseDto;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ConditionStatus;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.service.VenueService;
import dev.hidly.nubi.promotion.data.dto.response.PromotionResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {
    private static final Map<FacilityType, String> FACILITY_LABELS = Map.of(
            FacilityType.ELEVATOR, "승강기",
            FacilityType.ACCESSIBLE_TOILET, "장애인 화장실",
            FacilityType.RAMP, "경사로",
            FacilityType.NO_STEP, "높이 차이 없음",
            FacilityType.PARKING, "장애인 주차",
            FacilityType.BRAILLE_BLOCK, "점자블록");

    private final ProgramRepository programRepository;
    private final ProgramConditionRepository programConditionRepository;
    private final VenueService venueService;
    private final PromotionWriter promotionWriter;

    // 주최 기관만 쓸 수 있다. 저장하지 않고 매번 새로 쓴다("다시 쓰기"는 다시 부르기).
    @Transactional(readOnly = true)
    public GlobalResponse<PromotionResponseDto> write(Long eventId) {
        Program program = programRepository.findById(eventId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        if (!program.isHostedBy(SecurityUtil.getOrganizationId())) {
            throw new ProgramAccessDeniedException();
        }

        // 주최가 "제공한다"고 체크한 조건만 홍보한다. 후기·AI 출처나 확인 필요 항목은 넣지 않는다.
        List<ProgramCondition> provided = programConditionRepository.findAllByProgramIdOrderByIdAsc(eventId).stream()
                .filter(condition -> condition.getSource() == ConditionSource.HOST
                        && condition.getStatus() == ConditionStatus.PROVIDED)
                .toList();
        List<FacilityType> facilities = program.getVenue() == null ? List.of()
                : venueService.mergeFacilities(program.getVenue().getId()).stream()
                .filter(facility -> facility.status() == FacilityStatus.YES)
                .map(FacilityResponseDto::facilityType)
                .toList();

        PromotionWriter.Facts facts = new PromotionWriter.Facts(program.getTitle(), program.getDisplayHostName(),
                program.getStartAt(), program.getEndAt(),
                program.getVenue() == null ? null : program.getVenue().getName(),
                program.getCapacity(), program.getDescription(),
                provided.stream().map(condition -> condition.getSupportTag().getLabel()).toList(),
                provided.stream().map(condition -> condition.getSupportTag().getCode()).toList(),
                facilities.stream().map(FACILITY_LABELS::get).toList(),
                facilities.stream().map(Enum::name).toList());
        PromotionWriter.Result result = promotionWriter.write(facts);
        log.info("[writePromotion] 홍보문 작성: eventId={}, 지원 {}개, 편의시설 {}개, AI {}",
                eventId, provided.size(), facilities.size(), result.aiWritten());

        return GlobalResponse.ok(new PromotionResponseDto(eventId, result.aiWritten(), result.general(), result.easy(), result.sms()));
    }
}
