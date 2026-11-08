package dev.hidly.nubi.program.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.data.dto.request.EventConditionRequestDto;
import dev.hidly.nubi.program.data.dto.request.EventCreateRequestDto;
import dev.hidly.nubi.program.data.dto.response.EventSummaryResponseDto;
import dev.hidly.nubi.program.data.dto.response.FacilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramDetailResponseDto;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.exception.InvalidEventRequestException;
import dev.hidly.nubi.program.data.exception.VenueNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.data.repository.VenueRepository;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.exception.SupportTagNotFoundException;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {
    private static final Set<ProgramStatus> CREATABLE_STATUSES = Set.of(ProgramStatus.DRAFT, ProgramStatus.OPEN);
    private static final Sort MY_EVENTS_SORT = Sort.by(Sort.Order.asc("startAt").nullsLast(), Sort.Order.asc("id"));

    private final ProgramRepository programRepository;
    private final ProgramConditionRepository programConditionRepository;
    private final ProgramImageRepository programImageRepository;
    private final OrganizationRepository organizationRepository;
    private final VenueRepository venueRepository;
    private final SupportTagRepository supportTagRepository;
    private final VenueService venueService;

    @Transactional
    public GlobalResponse<ProgramDetailResponseDto> create(EventCreateRequestDto request) {
        validate(request);
        Long organizationId = SecurityUtil.getOrganizationId();
        Organization host = organizationRepository.getReferenceById(organizationId);
        Venue venue = request.getVenueId() == null ? null
                : venueRepository.findById(request.getVenueId()).orElseThrow(VenueNotFoundException::new);
        Map<Long, SupportTag> tags = findSupportTags(request.conditionsOrEmpty());

        Program program = programRepository.save(Program.event(host, venue, request.getTitle().trim(),
                request.getCategory(), request.getDescription(), request.getStartAt(), request.getEndAt(),
                request.getCapacity(), request.getContactPhone(), request.getDeadlineAt(), request.getStatus()));

        programConditionRepository.saveAll(request.conditionsOrEmpty().stream()
                .map(condition -> ProgramCondition.builder()
                        .program(program)
                        .supportTag(tags.get(condition.getSupportTagId()))
                        .status(condition.statusOrDefault())
                        .evidence(condition.getEvidence())
                        .source(ConditionSource.HOST)
                        .build())
                .toList());
        log.info("[createEvent] 행사 등록: organizationId={}, eventId={}, status={}, 조건 {}개",
                organizationId, program.getId(), program.getStatus(), request.conditionsOrEmpty().size());

        List<FacilityResponseDto> facilities = venue == null ? List.of() : venueService.mergeFacilities(venue.getId());
        return GlobalResponse.ok(ProgramDetailResponseDto.of(program, List.of(), facilities));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<PageResponse<EventSummaryResponseDto>> getMyEvents(List<ProgramStatus> statuses, int page, int size) {
        Long organizationId = SecurityUtil.getOrganizationId();
        Page<Program> events = programRepository.findAll(hostedBy(organizationId, statuses),
                PageRequest.of(page, size, MY_EVENTS_SORT));

        List<Long> eventIds = events.map(Program::getId).getContent();
        Map<Long, String> thumbnails = eventIds.isEmpty() ? Map.of()
                : programImageRepository.findAllByProgramIdInAndSortOrder(eventIds, 0).stream()
                .collect(Collectors.toMap(image -> image.getProgram().getId(), ProgramImage::getUrl, (a, b) -> a));

        return GlobalResponse.ok(PageResponse.from(
                events.map(event -> EventSummaryResponseDto.of(event, thumbnails.get(event.getId())))));
    }

    private void validate(EventCreateRequestDto request) {
        if (!CREATABLE_STATUSES.contains(request.getStatus())) {
            throw new InvalidEventRequestException();
        }
        if (request.getEndAt() != null && request.getEndAt().isBefore(request.getStartAt())) {
            throw new InvalidEventRequestException();
        }
        Set<Long> seen = new HashSet<>();
        for (EventConditionRequestDto condition : request.conditionsOrEmpty()) {
            if (!seen.add(condition.getSupportTagId())) {
                throw new InvalidEventRequestException();
            }
        }
    }

    private Map<Long, SupportTag> findSupportTags(List<EventConditionRequestDto> conditions) {
        if (conditions.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = conditions.stream().map(EventConditionRequestDto::getSupportTagId).toList();
        Map<Long, SupportTag> tags = supportTagRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SupportTag::getId, Function.identity()));
        if (tags.size() != ids.size()) {
            throw new SupportTagNotFoundException();
        }
        return tags;
    }

    private static Specification<Program> hostedBy(Long organizationId, List<ProgramStatus> statuses) {
        return (root, query, cb) -> {
            var predicate = cb.and(
                    cb.equal(root.get("hostOrganization").get("id"), organizationId),
                    cb.isNull(root.get("deletedAt")));
            if (statuses != null && !statuses.isEmpty()) {
                predicate = cb.and(predicate, root.get("status").in(statuses));
            }
            return predicate;
        };
    }
}
