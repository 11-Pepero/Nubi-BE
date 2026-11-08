package dev.hidly.nubi.support.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.service.EligibilityService;
import dev.hidly.nubi.program.service.EligibilityService.ResultCount;
import dev.hidly.nubi.support.data.dto.request.GroupRequestDto;
import dev.hidly.nubi.support.data.dto.request.GroupSupportRequestDto;
import dev.hidly.nubi.support.data.dto.response.GroupResponseDto;
import dev.hidly.nubi.support.data.dto.response.GroupSummaryResponseDto;
import dev.hidly.nubi.support.data.dto.response.SupportCountResponseDto;
import dev.hidly.nubi.support.data.entity.GroupSupport;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.exception.GroupAccessDeniedException;
import dev.hidly.nubi.support.data.exception.GroupNotFoundException;
import dev.hidly.nubi.support.data.exception.InvalidGroupSupportException;
import dev.hidly.nubi.support.data.exception.SupportTagNotFoundException;
import dev.hidly.nubi.support.data.repository.CarePersonRepository;
import dev.hidly.nubi.support.data.repository.SupportGroupRepository;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {
    private final SupportGroupRepository supportGroupRepository;
    private final CarePersonRepository carePersonRepository;
    private final EligibilityService eligibilityService;
    private final SupportTagRepository supportTagRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<List<GroupResponseDto>> getGroups() {
        Long organizationId = SecurityUtil.getOrganizationId();
        List<SupportGroup> groups = supportGroupRepository.findActiveWithSupports(organizationId);
        Map<Long, Long> careCounts = countCarePersons(groups.stream().map(SupportGroup::getId).toList());
        Map<Long, ResultCount> resultCounts = eligibilityService.countResults(groups, organizationId);

        return GlobalResponse.ok(groups.stream()
                .map(group -> GroupResponseDto.of(group, careCounts.getOrDefault(group.getId(), 0L),
                        resultCounts.getOrDefault(group.getId(), ResultCount.EMPTY)))
                .toList());
    }

    @Transactional
    public GlobalResponse<GroupResponseDto> createGroup(GroupRequestDto request) {
        Long organizationId = SecurityUtil.getOrganizationId();

        SupportGroup group = SupportGroup.builder()
                .organization(organizationRepository.getReferenceById(organizationId))
                .name(request.getName().trim())
                .memberCount(request.getMemberCount())
                .build();
        toGroupSupports(request).forEach(group::addSupport);

        supportGroupRepository.save(group);
        log.info("[createGroup] 그룹 생성: organizationId={}, groupId={}", organizationId, group.getId());

        return GlobalResponse.ok(toResponse(group));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<GroupResponseDto> getGroup(Long groupId) {
        return GlobalResponse.ok(toResponse(getOwnedGroup(groupId)));
    }

    @Transactional
    public GlobalResponse<GroupResponseDto> updateGroup(Long groupId, GroupRequestDto request) {
        SupportGroup group = getOwnedGroup(groupId);

        group.update(request.getName().trim(), request.getMemberCount());
        group.replaceSupports(toGroupSupports(request));
        supportGroupRepository.flush();
        log.info("[updateGroup] 그룹 수정: groupId={}", groupId);

        return GlobalResponse.ok(toResponse(group));
    }

    @Transactional
    public GlobalResponse<Void> deleteGroup(Long groupId) {
        SupportGroup group = getOwnedGroup(groupId);
        group.delete();
        int cleared = carePersonRepository.clearGroup(groupId);
        log.info("[deleteGroup] 그룹 삭제: groupId={}, 그룹이 비워진 개별 케어 {}명", groupId, cleared);
        return GlobalResponse.ok();
    }

    @Transactional(readOnly = true)
    public GlobalResponse<GroupSummaryResponseDto> getSummary() {
        List<SupportGroup> groups = supportGroupRepository.findActiveWithSupports(SecurityUtil.getOrganizationId());

        int memberCount = groups.stream().mapToInt(SupportGroup::getMemberCount).sum();

        Map<SupportTag, Integer> countByTag = new LinkedHashMap<>();
        for (SupportGroup group : groups) {
            for (GroupSupport support : group.getSupports()) {
                int count = support.getQuantity() != null ? support.getQuantity() : group.getMemberCount();
                countByTag.merge(support.getSupportTag(), count, Integer::sum);
            }
        }

        List<SupportCountResponseDto> supports = countByTag.entrySet().stream()
                .sorted(Comparator.<Map.Entry<SupportTag, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().getId()))
                .map(entry -> new SupportCountResponseDto(
                        entry.getKey().getId(),
                        entry.getKey().getCategory(),
                        entry.getKey().getCode(),
                        entry.getKey().getLabel(),
                        entry.getValue()))
                .toList();

        return GlobalResponse.ok(new GroupSummaryResponseDto(groups.size(), memberCount, supports));
    }

    private GroupResponseDto toResponse(SupportGroup group) {
        long careCount = countCarePersons(List.of(group.getId())).getOrDefault(group.getId(), 0L);
        ResultCount resultCount = eligibilityService.countResults(List.of(group), group.getOrganization().getId())
                .getOrDefault(group.getId(), ResultCount.EMPTY);
        return GroupResponseDto.of(group, careCount, resultCount);
    }

    private Map<Long, Long> countCarePersons(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return carePersonRepository.countActiveByGroupIds(groupIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private SupportGroup getOwnedGroup(Long groupId) {
        SupportGroup group = supportGroupRepository.findActiveWithSupportsById(groupId)
                .orElseThrow(GroupNotFoundException::new);

        if (!group.isOwnedBy(SecurityUtil.getOrganizationId())) {
            log.warn("[getOwnedGroup] 다른 기관 그룹 접근 시도: groupId={}", groupId);
            throw new GroupAccessDeniedException();
        }
        return group;
    }

    private List<GroupSupport> toGroupSupports(GroupRequestDto request) {
        List<GroupSupportRequestDto> supports = request.supportsOrEmpty();

        Set<Long> tagIds = new HashSet<>();
        for (GroupSupportRequestDto support : supports) {
            if (!tagIds.add(support.getSupportTagId())) {
                log.warn("[toGroupSupports] 같은 지원 항목 중복: supportTagId={}", support.getSupportTagId());
                throw new InvalidGroupSupportException();
            }
            if (support.getQuantity() != null && support.getQuantity() > request.getMemberCount()) {
                log.warn("[toGroupSupports] 필요 인원이 그룹 인원보다 많음: supportTagId={}", support.getSupportTagId());
                throw new InvalidGroupSupportException();
            }
        }

        Map<Long, SupportTag> tags = supportTagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(SupportTag::getId, Function.identity()));
        if (tags.size() != tagIds.size()) {
            throw new SupportTagNotFoundException();
        }

        return supports.stream()
                .map(support -> GroupSupport.builder()
                        .supportTag(tags.get(support.getSupportTagId()))
                        .quantity(support.getQuantity())
                        .ratio(support.getRatio())
                        .note(support.getNote() == null || support.getNote().isBlank() ? null : support.getNote().trim())
                        .build())
                .toList();
    }
}
