package dev.hidly.nubi.program.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.program.data.dto.request.ProgramSearchCondition;
import dev.hidly.nubi.program.data.dto.response.EligibilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.FacilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramDetailResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramImageResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramSummaryResponseDto;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.data.repository.ProgramSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramService {
    private static final Sort SEARCH_SORT = Sort.by(Sort.Order.asc("startAt").nullsLast(), Sort.Order.asc("id"));

    private final ProgramRepository programRepository;
    private final ProgramImageRepository programImageRepository;
    private final VenueService venueService;
    private final EligibilityService eligibilityService;

    @Transactional(readOnly = true)
    public GlobalResponse<PageResponse<ProgramSummaryResponseDto>> search(ProgramSearchCondition condition, int page, int size) {
        Page<Program> programs = programRepository.findAll(ProgramSpecifications.search(condition),
                PageRequest.of(page, size, SEARCH_SORT));

        List<Long> programIds = programs.map(Program::getId).getContent();
        Map<Long, String> thumbnails = programIds.isEmpty() ? Map.of()
                : programImageRepository.findAllByProgramIdInAndSortOrder(programIds, 0).stream()
                .collect(Collectors.toMap(image -> image.getProgram().getId(), ProgramImage::getUrl, (a, b) -> a));

        return GlobalResponse.ok(PageResponse.from(
                programs.map(program -> ProgramSummaryResponseDto.of(program, thumbnails.get(program.getId())))));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<ProgramDetailResponseDto> getDetail(Long programId, Long groupId) {
        Program program = programRepository.findWithVenueById(programId)
                .filter(p -> !p.isDeleted())
                .filter(this::isVisible)
                .orElseThrow(ProgramNotFoundException::new);

        List<ProgramImageResponseDto> images = programImageRepository.findAllByProgramIdOrderBySortOrderAsc(programId)
                .stream()
                .map(ProgramImageResponseDto::from)
                .toList();
        List<FacilityResponseDto> facilities = program.getVenue() == null ? List.of()
                : venueService.mergeFacilities(program.getVenue().getId());

        EligibilityResponseDto eligibility = groupId == null ? null : eligibilityService.judgeProgram(program, groupId);

        return GlobalResponse.ok(ProgramDetailResponseDto.of(program, images, facilities, eligibility));
    }

    private boolean isVisible(Program program) {
        if (program.getStatus() != ProgramStatus.DRAFT) {
            return true;
        }
        return SecurityUtil.findOrganizationId().map(program::isHostedBy).orElse(false);
    }
}
