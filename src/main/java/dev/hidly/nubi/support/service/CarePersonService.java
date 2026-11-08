package dev.hidly.nubi.support.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.repository.OrganizationRepository;
import dev.hidly.nubi.program.service.EligibilityService;
import dev.hidly.nubi.support.data.dto.request.CareItemRequestDto;
import dev.hidly.nubi.support.data.dto.request.CarePersonCreateRequestDto;
import dev.hidly.nubi.support.data.dto.request.CarePersonRequest;
import dev.hidly.nubi.support.data.dto.request.CarePersonUpdateRequestDto;
import dev.hidly.nubi.support.data.dto.response.CarePersonResponseDto;
import dev.hidly.nubi.support.data.entity.CareItem;
import dev.hidly.nubi.support.data.entity.CarePerson;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import dev.hidly.nubi.support.data.exception.CarePersonAccessDeniedException;
import dev.hidly.nubi.support.data.exception.CarePersonNotFoundException;
import dev.hidly.nubi.support.data.exception.GroupAccessDeniedException;
import dev.hidly.nubi.support.data.exception.GroupNotFoundException;
import dev.hidly.nubi.support.data.exception.SupportTagNotFoundException;
import dev.hidly.nubi.support.data.repository.CarePersonRepository;
import dev.hidly.nubi.support.data.repository.SupportGroupRepository;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarePersonService {
    private final CarePersonRepository carePersonRepository;
    private final SupportGroupRepository supportGroupRepository;
    private final SupportTagRepository supportTagRepository;
    private final OrganizationRepository organizationRepository;
    private final EligibilityService eligibilityService;

    @Transactional(readOnly = true)
    public GlobalResponse<List<CarePersonResponseDto>> getCarePersons(Long groupId, String keyword, SupportCategory category) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<CarePersonResponseDto> carePersons = carePersonRepository
                .findActiveWithItems(SecurityUtil.getOrganizationId()).stream()
                .filter(c -> groupId == null || (c.getGroup() != null && c.getGroup().getId().equals(groupId)))
                .filter(c -> normalizedKeyword == null || c.getAlias().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(c -> category == null || c.getItems().stream()
                        .anyMatch(item -> item.getSupportTag() != null && item.getSupportTag().getCategory() == category))
                .map(CarePersonResponseDto::from)
                .toList();

        return GlobalResponse.ok(carePersons);
    }

    @Transactional
    public GlobalResponse<CarePersonResponseDto> createCarePerson(CarePersonCreateRequestDto request) {
        Long organizationId = SecurityUtil.getOrganizationId();

        CarePerson carePerson = CarePerson.builder()
                .organization(organizationRepository.getReferenceById(organizationId))
                .group(request.getGroupId() == null ? null : getOwnedGroup(request.getGroupId()))
                .alias(request.getAlias().trim())
                .memo(trimToNull(request.getMemo()))
                .applyToJudgement(request.applyToJudgementOrDefault())
                .build();
        toCareItems(request).forEach(carePerson::addItem);

        carePersonRepository.save(carePerson);
        recomputeGroup(carePerson);
        log.info("[createCarePerson] 개별 케어 추가: organizationId={}, careId={}", organizationId, carePerson.getId());

        return GlobalResponse.ok(CarePersonResponseDto.from(carePerson));
    }

    @Transactional(readOnly = true)
    public GlobalResponse<CarePersonResponseDto> getCarePerson(Long careId) {
        return GlobalResponse.ok(CarePersonResponseDto.from(getOwnedCarePerson(careId)));
    }

    @Transactional
    public GlobalResponse<CarePersonResponseDto> updateCarePerson(Long careId, CarePersonUpdateRequestDto request) {
        CarePerson carePerson = getOwnedCarePerson(careId);

        carePerson.update(request.getAlias().trim(), trimToNull(request.getMemo()), request.applyToJudgementOrDefault());
        carePerson.replaceItems(toCareItems(request));
        carePersonRepository.flush();
        recomputeGroup(carePerson);
        log.info("[updateCarePerson] 개별 케어 수정: careId={}", careId);

        return GlobalResponse.ok(CarePersonResponseDto.from(carePerson));
    }

    @Transactional
    public GlobalResponse<Void> deleteCarePerson(Long careId) {
        CarePerson carePerson = getOwnedCarePerson(careId);
        carePerson.delete();
        carePersonRepository.flush();
        recomputeGroup(carePerson);
        log.info("[deleteCarePerson] 개별 케어 삭제: careId={}", careId);
        return GlobalResponse.ok();
    }

    private void recomputeGroup(CarePerson carePerson) {
        if (carePerson.getGroup() != null) {
            eligibilityService.recompute(carePerson.getGroup());
        }
    }

    private CarePerson getOwnedCarePerson(Long careId) {
        CarePerson carePerson = carePersonRepository.findActiveWithItemsById(careId)
                .orElseThrow(CarePersonNotFoundException::new);

        if (!carePerson.isOwnedBy(SecurityUtil.getOrganizationId())) {
            log.warn("[getOwnedCarePerson] 다른 기관 개별 케어 접근 시도: careId={}", careId);
            throw new CarePersonAccessDeniedException();
        }
        return carePerson;
    }

    private SupportGroup getOwnedGroup(Long groupId) {
        SupportGroup group = supportGroupRepository.findById(groupId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(GroupNotFoundException::new);

        if (!group.isOwnedBy(SecurityUtil.getOrganizationId())) {
            log.warn("[getOwnedGroup] 다른 기관 그룹으로 개별 케어 등록 시도: groupId={}", groupId);
            throw new GroupAccessDeniedException();
        }
        return group;
    }

    private List<CareItem> toCareItems(CarePersonRequest request) {
        List<CareItemRequestDto> items = request.itemsOrEmpty();

        Set<Long> tagIds = items.stream()
                .map(CareItemRequestDto::getSupportTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SupportTag> tags = supportTagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(SupportTag::getId, Function.identity()));
        if (tags.size() != tagIds.size()) {
            throw new SupportTagNotFoundException();
        }

        return items.stream()
                .map(item -> CareItem.builder()
                        .supportTag(item.getSupportTagId() == null ? null : tags.get(item.getSupportTagId()))
                        .content(trimToNull(item.getContent()))
                        .build())
                .toList();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
