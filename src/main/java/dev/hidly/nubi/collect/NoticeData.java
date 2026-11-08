package dev.hidly.nubi.collect;

import dev.hidly.nubi.program.data.enums.ProgramCategory;

import java.time.LocalDateTime;
import java.util.Map;

public record NoticeData(
        String dataset,
        String externalId,
        Map<String, Object> raw,
        String title,
        ProgramCategory category,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String placeName,
        String address,
        String contactPhone,
        String description,
        String sourceUrl
) {
}
