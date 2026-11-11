package dev.hidly.nubi.promotion.service;

import dev.hidly.nubi.common.file.FileStorageService;
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
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.service.VenueService;
import dev.hidly.nubi.promotion.data.dto.response.PromotionResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ProgramImageRepository programImageRepository;
    private final FileStorageService fileStorageService;
    private final PosterPdfRenderer posterPdfRenderer;

    public record PosterFile(String fileName, byte[] content) {
    }

    // 주최 기관만 쓸 수 있다. 저장하지 않고 매번 새로 쓴다("다시 쓰기"는 다시 부르기).
    @Transactional(readOnly = true)
    public GlobalResponse<PromotionResponseDto> write(Long eventId) {
        Program program = getHostedEvent(eventId);
        PromotionWriter.Facts facts = factsOf(program);
        PromotionWriter.Result result = promotionWriter.write(facts);
        log.info("[writePromotion] 홍보문 작성: eventId={}, 지원 {}개, 편의시설 {}개, AI {}",
                eventId, facts.supports().size(), facts.facilities().size(), result.aiWritten());

        return GlobalResponse.ok(new PromotionResponseDto(eventId, result.aiWritten(), result.general(), result.easy(), result.sms()));
    }

    // 행사 정보·쉬운 말 홍보문·대표 사진을 A4 포스터 PDF로 만든다("포스터로 받기"). AI는 다시 부르지 않는다.
    @Transactional(readOnly = true)
    public PosterFile poster(Long eventId, String editedEasy) {
        Program program = getHostedEvent(eventId);
        PromotionWriter.Facts facts = factsOf(program);
        String easy = editedEasy == null || editedEasy.isBlank() ? promotionWriter.easy(facts) : editedEasy.trim();

        byte[] photo = programImageRepository.findAllByProgramIdOrderBySortOrderAsc(eventId).stream()
                .findFirst()
                .map(image -> readUpload(image.getUrl()))
                .orElse(null);

        byte[] pdf = posterPdfRenderer.render(new PosterPdfRenderer.Poster(facts.title(), facts.category(),
                PromotionWriter.hook(facts.category()), facts.hostName(), facts.startAt(), facts.endAt(), facts.venueName(),
                program.getVenue() == null ? null : program.getVenue().getAddress(), facts.capacity(), facts.deadlineAt(),
                facts.applyMethod(), program.getApplyUrl(), facts.contactPhone(), easy, facts.supports(), facts.facilities(), photo));
        log.info("[poster] 포스터 PDF: eventId={}, 사진 {}, 고친 문구 {}", eventId, photo != null, editedEasy != null && !editedEasy.isBlank());
        return new PosterFile((facts.title() + "_포스터.pdf").replaceAll("[\\\\/:*?\"<>|]", "_"), pdf);
    }

    // 업로드 폴더 밖은 읽지 않는다. 파일이 없거나 못 읽으면 사진 없이 만든다.
    private byte[] readUpload(String url) {
        if (url == null || !url.startsWith(FileStorageService.URL_PREFIX)) {
            return null;
        }
        Path root = fileStorageService.getRoot();
        Path file = root.resolve(url.substring(FileStorageService.URL_PREFIX.length())).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("[poster] 대표 사진을 읽지 못함: {}", file, e);
            return null;
        }
    }

    private Program getHostedEvent(Long eventId) {
        Program program = programRepository.findById(eventId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        if (!program.isHostedBy(SecurityUtil.getOrganizationId())) {
            throw new ProgramAccessDeniedException();
        }
        return program;
    }

    private PromotionWriter.Facts factsOf(Program program) {
        // 주최가 "제공한다"고 체크한 조건만 홍보한다. 후기·AI 출처나 확인 필요 항목은 넣지 않는다.
        List<ProgramCondition> provided = programConditionRepository.findAllByProgramIdOrderByIdAsc(program.getId()).stream()
                .filter(condition -> condition.getSource() == ConditionSource.HOST
                        && condition.getStatus() == ConditionStatus.PROVIDED)
                .toList();
        List<FacilityType> facilities = program.getVenue() == null ? List.of()
                : venueService.mergeFacilities(program.getVenue().getId()).stream()
                .filter(facility -> facility.status() == FacilityStatus.YES)
                .map(FacilityResponseDto::facilityType)
                .toList();

        return new PromotionWriter.Facts(program.getTitle(), program.getDisplayHostName(),
                program.getStartAt(), program.getEndAt(),
                program.getVenue() == null ? null : program.getVenue().getName(),
                program.getCapacity(), program.getDescription(),
                provided.stream().map(condition -> condition.getSupportTag().getLabel()).toList(),
                provided.stream().map(condition -> condition.getSupportTag().getCode()).toList(),
                facilities.stream().map(FACILITY_LABELS::get).toList(),
                facilities.stream().map(Enum::name).toList(),
                program.getCategory() == null ? null : program.getCategory().name(),
                program.getDeadlineAt(),
                applyMethodLabel(program),
                program.getContactPhone());
    }

    // 홍보문·포스터에 쓰는 신청 방법 한 줄
    static String applyMethodLabel(Program program) {
        if (program.getApplyMethod() == null) {
            return null;
        }
        return switch (program.getApplyMethod()) {
            case DIRECT -> "누비에서 신청";
            case ONLINE -> "온라인 신청";
            case PHONE -> "전화 신청";
            case PAPER -> "신청서 제출";
        };
    }
}
