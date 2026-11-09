package dev.hidly.nubi.program.service;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.response.PageResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.program.data.dto.response.EligibilityResponseDto;
import dev.hidly.nubi.program.data.dto.response.EligibleProgramResponseDto;
import dev.hidly.nubi.program.data.dto.response.JudgementImpactResponseDto;
import dev.hidly.nubi.program.data.dto.response.ProgramSummaryResponseDto;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.entity.ProgramImage;
import dev.hidly.nubi.program.data.enums.EligibilityResult;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.enums.ItemResult;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramConditionRepository;
import dev.hidly.nubi.program.data.repository.ProgramImageRepository;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.service.EligibilityJudge.Judgement;
import dev.hidly.nubi.program.service.EligibilityJudge.ProgramFacts;
import dev.hidly.nubi.support.data.entity.CarePerson;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import dev.hidly.nubi.support.data.exception.CarePersonAccessDeniedException;
import dev.hidly.nubi.support.data.exception.CarePersonNotFoundException;
import dev.hidly.nubi.support.data.exception.GroupAccessDeniedException;
import dev.hidly.nubi.support.data.exception.GroupNotFoundException;
import dev.hidly.nubi.support.data.repository.CarePersonRepository;
import dev.hidly.nubi.support.data.repository.SupportGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EligibilityService {
    private static final Comparator<Judged> LIST_ORDER = Comparator
            .comparing((Judged judged) -> judged.judgement().result())
            .thenComparing(judged -> judged.program().getStartAt(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(judged -> judged.program().getId());

    private static final int IMPACT_LIMIT = 20;

    private final ProgramRepository programRepository;
    private final ProgramConditionRepository programConditionRepository;
    private final ProgramImageRepository programImageRepository;
    private final ApplicationRepository applicationRepository;
    private final SupportGroupRepository supportGroupRepository;
    private final CarePersonRepository carePersonRepository;
    private final VenueService venueService;
    private final EligibilityJudge eligibilityJudge;

    public record ResultCount(long eligible, long check, long not) {
        public static final ResultCount EMPTY = new ResultCount(0, 0, 0);
    }

    private record Judged(Program program, Judgement judgement) {
    }

    @Transactional(readOnly = true)
    public GlobalResponse<PageResponse<EligibleProgramResponseDto>> getEligiblePrograms(
            Long groupId, List<Long> careIds, EligibilityResult result, YearMonth month, int page, int size) {
        Long organizationId = SecurityUtil.getOrganizationId();
        SupportGroup group = getOwnedGroup(groupId, organizationId);
        List<CarePerson> carePersons = resolveCarePersons(group, careIds, organizationId);

        List<Program> programs = openPrograms().stream().filter(program -> inMonth(program, month)).toList();
        List<Judged> judged = loadFacts(programs).stream()
                .map(facts -> new Judged(facts.program(), eligibilityJudge.judge(group, carePersons, facts)))
                .filter(item -> result == null || item.judgement().result() == result)
                .sorted(LIST_ORDER)
                .toList();

        int from = (int) Math.min((long) page * size, judged.size());
        List<Judged> pageItems = judged.subList(from, (int) Math.min((long) from + size, judged.size()));
        Map<Long, String> thumbnails = thumbnails(pageItems.stream().map(item -> item.program().getId()).toList());

        List<EligibleProgramResponseDto> content = pageItems.stream()
                .map(item -> EligibleProgramResponseDto.of(
                        ProgramSummaryResponseDto.of(item.program(), thumbnails.get(item.program().getId())),
                        item.judgement().result(), item.judgement().reasons()))
                .toList();
        int totalPages = (int) Math.ceil((double) judged.size() / size);
        return GlobalResponse.ok(new PageResponse<>(content, page, size, judged.size(), totalPages));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<EligibilityResponseDto> getEligibility(Long programId, Long groupId, List<Long> careIds) {
        Long organizationId = SecurityUtil.getOrganizationId();
        Program program = programRepository.findWithVenueById(programId)
                .filter(p -> !p.isDeleted())
                .filter(p -> p.getStatus() != ProgramStatus.DRAFT || p.isHostedBy(organizationId))
                .orElseThrow(ProgramNotFoundException::new);
        SupportGroup group = getOwnedGroup(groupId, organizationId);
        return GlobalResponse.ok(judgeOne(program, group, resolveCarePersons(group, careIds, organizationId)));
    }

    @Transactional(readOnly = true)
    public EligibilityResponseDto judgeProgram(Program program, Long groupId) {
        Long organizationId = SecurityUtil.getOrganizationId();
        SupportGroup group = getOwnedGroup(groupId, organizationId);
        return judgeOne(program, group, resolveCarePersons(group, null, organizationId));
    }

    @Transactional(readOnly = true)
    public Map<Long, ResultCount> countResults(List<SupportGroup> groups, Long organizationId) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        List<CarePerson> allCarePersons = carePersonRepository.findActiveWithItems(organizationId);
        List<ProgramFacts> facts = loadFacts(openPrograms());

        Map<Long, ResultCount> counts = new HashMap<>();
        for (SupportGroup group : groups) {
            List<CarePerson> carePersons = defaultCarePersons(group, allCarePersons);
            Map<EligibilityResult, Long> byResult = facts.stream()
                    .map(fact -> eligibilityJudge.judge(group, carePersons, fact).result())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            counts.put(group.getId(), new ResultCount(
                    byResult.getOrDefault(EligibilityResult.ELIGIBLE, 0L),
                    byResult.getOrDefault(EligibilityResult.CHECK, 0L),
                    byResult.getOrDefault(EligibilityResult.NOT, 0L)));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public JudgementImpactResponseDto impactOf(CarePerson target) {
        SupportGroup group = target.getGroup();
        if (group == null || !target.isApplyToJudgement()) {
            return JudgementImpactResponseDto.NOT_APPLIED;
        }

        List<CarePerson> withTarget = defaultCarePersons(group,
                carePersonRepository.findActiveWithItems(group.getOrganization().getId()));
        List<CarePerson> withoutTarget = withTarget.stream()
                .filter(care -> !care.getId().equals(target.getId()))
                .toList();

        List<JudgementImpactResponseDto.AffectedProgram> affected = new ArrayList<>();
        for (ProgramFacts facts : loadFacts(openPrograms())) {
            Judgement with = eligibilityJudge.judge(group, withTarget, facts);
            Judgement without = eligibilityJudge.judge(group, withoutTarget, facts);
            if (with.result() == without.result()) {
                continue;
            }
            Program program = facts.program();
            affected.add(new JudgementImpactResponseDto.AffectedProgram(program.getId(), program.getTitle(),
                    program.getStartAt(), without.result(), with.result(),
                    with.reasons().careChecks().stream()
                            .filter(check -> check.careId().equals(target.getId()))
                            .filter(check -> check.result() != ItemResult.MET && check.result() != ItemResult.NOTE)
                            .toList()));
        }

        affected.sort(Comparator
                .comparing(JudgementImpactResponseDto.AffectedProgram::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JudgementImpactResponseDto.AffectedProgram::programId));
        return new JudgementImpactResponseDto(true, affected.size(), affected.stream().limit(IMPACT_LIMIT).toList());
    }

    private EligibilityResponseDto judgeOne(Program program, SupportGroup group, List<CarePerson> carePersons) {
        Judgement judgement = eligibilityJudge.judge(group, carePersons, loadFacts(List.of(program)).getFirst());
        return new EligibilityResponseDto(program.getId(), group.getId(), judgement.result(), judgement.reasons());
    }

    private List<Program> openPrograms() {
        return programRepository.findJudgeable(LocalDateTime.now());
    }

    private List<ProgramFacts> loadFacts(List<Program> programs) {
        if (programs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ProgramCondition>> conditions = programConditionRepository
                .findAllByProgramIdIn(programs.stream().map(Program::getId).toList()).stream()
                .collect(Collectors.groupingBy(condition -> condition.getProgram().getId()));
        Map<Long, Long> takenSeats = applicationRepository.sumParticipantsByProgramIds(
                        programs.stream().map(Program::getId).toList(), ApplicationStatus.NOT_HOLDING_SEAT).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).longValue()));
        Map<Long, Map<FacilityType, FacilityStatus>> facilities = venueService.mergeStatusesByVenue(programs.stream()
                .filter(program -> program.getVenue() != null)
                .map(program -> program.getVenue().getId())
                .collect(Collectors.toSet()));

        return programs.stream()
                .map(program -> new ProgramFacts(program,
                        conditions.getOrDefault(program.getId(), List.of()),
                        program.getVenue() == null ? Map.of()
                                : facilities.getOrDefault(program.getVenue().getId(), Map.of()),
                        takenSeats.getOrDefault(program.getId(), 0L)))
                .toList();
    }

    private boolean inMonth(Program program, YearMonth month) {
        if (month == null) {
            return true;
        }
        if (program.getStartAt() == null) {
            return false;
        }
        LocalDateTime monthStart = month.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime endAt = program.getEndAt() == null ? program.getStartAt() : program.getEndAt();
        return program.getStartAt().isBefore(nextMonthStart) && !endAt.isBefore(monthStart);
    }

    private Map<Long, String> thumbnails(List<Long> programIds) {
        if (programIds.isEmpty()) {
            return Map.of();
        }
        return programImageRepository.findAllByProgramIdInAndSortOrder(programIds, 0).stream()
                .collect(Collectors.toMap(image -> image.getProgram().getId(), ProgramImage::getUrl, (a, b) -> a));
    }

    private SupportGroup getOwnedGroup(Long groupId, Long organizationId) {
        SupportGroup group = supportGroupRepository.findActiveWithSupportsById(groupId)
                .orElseThrow(GroupNotFoundException::new);
        if (!group.isOwnedBy(organizationId)) {
            throw new GroupAccessDeniedException();
        }
        return group;
    }

    private List<CarePerson> defaultCarePersons(SupportGroup group, List<CarePerson> allCarePersons) {
        return allCarePersons.stream()
                .filter(CarePerson::isApplyToJudgement)
                .filter(care -> care.getGroup() != null && care.getGroup().getId().equals(group.getId()))
                .toList();
    }

    private List<CarePerson> resolveCarePersons(SupportGroup group, List<Long> careIds, Long organizationId) {
        List<CarePerson> allCarePersons = carePersonRepository.findActiveWithItems(organizationId);
        if (careIds == null || careIds.isEmpty()) {
            return defaultCarePersons(group, allCarePersons);
        }

        Map<Long, CarePerson> owned = allCarePersons.stream()
                .collect(Collectors.toMap(CarePerson::getId, Function.identity()));
        List<CarePerson> selected = new ArrayList<>();
        for (Long careId : new LinkedHashSet<>(careIds)) {
            CarePerson carePerson = owned.get(careId);
            if (carePerson == null) {
                boolean otherOrganization = carePersonRepository.findById(careId)
                        .filter(found -> found.getDeletedAt() == null)
                        .isPresent();
                throw otherOrganization ? new CarePersonAccessDeniedException() : new CarePersonNotFoundException();
            }
            if (carePerson.isApplyToJudgement()) {
                selected.add(carePerson);
            }
        }
        return selected;
    }
}
