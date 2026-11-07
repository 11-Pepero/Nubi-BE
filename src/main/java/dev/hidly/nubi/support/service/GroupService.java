package dev.hidly.nubi.support.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.support.data.dto.request.GroupRequestDto;
import dev.hidly.nubi.support.data.dto.request.GroupSupportRequestDto;
import dev.hidly.nubi.support.data.dto.response.GroupResponseDto;
import dev.hidly.nubi.support.data.dto.response.GroupSummaryResponseDto;
import dev.hidly.nubi.support.data.entity.GroupSupport;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import dev.hidly.nubi.support.data.exception.GroupAccessDeniedException;
import dev.hidly.nubi.support.data.exception.GroupNotFoundException;
import dev.hidly.nubi.support.data.exception.InvalidGroupSupportException;
import dev.hidly.nubi.support.data.exception.SupportTagNotFoundException;
import dev.hidly.nubi.support.data.repository.SupportGroupRepository;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {
    private static final Set<String> WHEELCHAIR_CODES = Set.of("WHEELCHAIR", "POWER_WHEELCHAIR");

    private final SupportGroupRepository supportGroupRepository;
    private final SupportTagRepository supportTagRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<List<GroupResponseDto>> getGroups() {
        List<GroupResponseDto> groups = supportGroupRepository
                .findActiveWithSupports(SecurityUtil.getOrganizationId()).stream()
                .map(GroupResponseDto::from)
                .toList();
        return GlobalResponse.ok(groups);
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

        return GlobalResponse.ok(GroupResponseDto.from(group));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<GroupResponseDto> getGroup(Long groupId) {
        return GlobalResponse.ok(GroupResponseDto.from(getOwnedGroup(groupId)));
    }

    @Transactional
    public GlobalResponse<GroupResponseDto> updateGroup(Long groupId, GroupRequestDto request) {
        SupportGroup group = getOwnedGroup(groupId);

        group.update(request.getName().trim(), request.getMemberCount());
        group.replaceSupports(toGroupSupports(request));
        supportGroupRepository.flush();
        log.info("[updateGroup] 그룹 수정: groupId={}", groupId);

        return GlobalResponse.ok(GroupResponseDto.from(group));
    }

    @Transactional
    public GlobalResponse<Void> deleteGroup(Long groupId) {
        SupportGroup group = getOwnedGroup(groupId);
        group.delete();
        log.info("[deleteGroup] 그룹 삭제: groupId={}", groupId);
        return GlobalResponse.ok();
    }

    @Transactional(readOnly = true)
    public GlobalResponse<GroupSummaryResponseDto> getSummary() {
        List<SupportGroup> groups = supportGroupRepository.findActiveWithSupports(SecurityUtil.getOrganizationId());

        int memberCount = groups.stream().mapToInt(SupportGroup::getMemberCount).sum();
        int wheelchairCount = sumQuantity(groups, tag -> WHEELCHAIR_CODES.contains(tag.getCode()));
        int companionCount = sumQuantity(groups, tag -> tag.getCategory() == SupportCategory.COMPANION);

        return GlobalResponse.ok(new GroupSummaryResponseDto(groups.size(), memberCount, wheelchairCount, companionCount));
    }

    private int sumQuantity(List<SupportGroup> groups, Predicate<SupportTag> tagFilter) {
        return groups.stream()
                .flatMap(group -> group.getSupports().stream())
                .filter(support -> tagFilter.test(support.getSupportTag()))
                .map(GroupSupport::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
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
