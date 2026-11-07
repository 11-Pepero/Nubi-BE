package dev.hidly.nubi.support.service;

import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.support.data.dto.response.SupportCategoryResponseDto;
import dev.hidly.nubi.support.data.dto.response.SupportTagResponseDto;
import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import dev.hidly.nubi.support.data.repository.SupportTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupportTagService {
    private final SupportTagRepository supportTagRepository;

    @Transactional(readOnly = true)
    public GlobalResponse<List<SupportCategoryResponseDto>> getSupportTags(SupportCategory category) {
        List<SupportTag> tags = category == null
                ? supportTagRepository.findAllByOrderByIdAsc()
                : supportTagRepository.findAllByCategoryOrderByIdAsc(category);

        Map<SupportCategory, List<SupportTagResponseDto>> byCategory = tags.stream()
                .collect(Collectors.groupingBy(SupportTag::getCategory,
                        Collectors.mapping(SupportTagResponseDto::from, Collectors.toList())));

        List<SupportCategoryResponseDto> response = Arrays.stream(SupportCategory.values())
                .filter(byCategory::containsKey)
                .map(c -> new SupportCategoryResponseDto(c, c.getLabel(), byCategory.get(c)))
                .toList();

        return GlobalResponse.ok(response);
    }
}
