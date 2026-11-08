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
import java.util.List;

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
    public int save(List<NoticeData> notices) {
        LocalDateTime now = LocalDateTime.now();
        int saved = 0;
        for (NoticeData notice : notices) {
            if (rawNoticeRepository.existsByExternalId(notice.externalId())) {
                continue;
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
                    .contactPhone(notice.contactPhone())
                    .applyUrl(truncate(notice.sourceUrl(), 300))
                    .status(notice.endAt() != null && notice.endAt().isBefore(now) ? ProgramStatus.ENDED : ProgramStatus.OPEN)
                    .build());
            saved++;
        }
        return saved;
    }

    @Transactional
    public int fillMissingLocations() {
        return venueResolver.fillMissingLocations();
    }

    @Transactional
    public int endExpired() {
        List<Program> expired = programRepository.findCollectedEndedBefore(ProgramStatus.OPEN, LocalDateTime.now());
        expired.forEach(Program::end);
        return expired.size();
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
