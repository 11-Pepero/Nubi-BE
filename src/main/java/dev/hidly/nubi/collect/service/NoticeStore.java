package dev.hidly.nubi.collect.service;

import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.RawNotice;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import dev.hidly.nubi.program.data.repository.RawNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NoticeStore {
    private final RawNoticeRepository rawNoticeRepository;
    private final ProgramRepository programRepository;
    private final VenueResolver venueResolver;

    @Transactional(readOnly = true)
    public boolean exists(String externalId) {
        return rawNoticeRepository.existsByExternalId(externalId);
    }

    @Transactional
    public boolean save(NoticeData notice) {
        if (rawNoticeRepository.existsByExternalId(notice.externalId())) {
            return false;
        }
        RawNotice rawNotice = rawNoticeRepository.save(RawNotice.builder()
                .dataset(notice.dataset())
                .externalId(notice.externalId())
                .rawJson(notice.raw())
                .build());
        programRepository.save(Program.builder()
                .rawNotice(rawNotice)
                .venue(venueResolver.resolve(notice.placeName(), notice.address()))
                .title(truncate(notice.title(), 150))
                .category(notice.category())
                .description(notice.description())
                .startAt(notice.startAt())
                .endAt(notice.endAt())
                .contactPhone(truncate(notice.contactPhone(), 20))
                .applyUrl(truncate(notice.sourceUrl(), 300))
                .status(notice.endAt() != null && notice.endAt().isBefore(LocalDateTime.now())
                        ? ProgramStatus.ENDED : ProgramStatus.OPEN)
                .build());
        return true;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
