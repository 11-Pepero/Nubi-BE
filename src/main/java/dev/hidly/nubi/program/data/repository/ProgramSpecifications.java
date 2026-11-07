package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.dto.request.ProgramSearchCondition;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.entity.Venue;
import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ProgramSpecifications {
    public static final Set<ProgramStatus> SEARCHABLE_STATUSES = Set.of(ProgramStatus.OPEN, ProgramStatus.CLOSED);
    public static final Set<FacilityType> ACCESSIBILITY_FACILITIES = Set.of(
            FacilityType.ELEVATOR, FacilityType.ACCESSIBLE_TOILET, FacilityType.RAMP, FacilityType.NO_STEP);

    private ProgramSpecifications() {
    }

    public static Specification<Program> search(ProgramSearchCondition condition) {
        return (root, query, cb) -> {
            Join<Program, Venue> venue = root.join("venue", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(root.get("status").in(SEARCHABLE_STATUSES));

            if (condition.keyword() != null && !condition.keyword().isBlank()) {
                String pattern = "%" + condition.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("hostName")), pattern),
                        cb.like(cb.lower(venue.get("name")), pattern)));
            }
            if (condition.category() != null) {
                predicates.add(cb.equal(root.get("category"), condition.category()));
            }
            if (condition.district() != null) {
                predicates.add(cb.equal(venue.get("district"), condition.district()));
            }
            if (condition.applyMethod() != null) {
                predicates.add(cb.equal(root.get("applyMethod"), condition.applyMethod()));
            }
            if (condition.month() != null) {
                LocalDateTime monthStart = condition.month().atDay(1).atStartOfDay();
                LocalDateTime nextMonthStart = condition.month().plusMonths(1).atDay(1).atStartOfDay();
                Expression<LocalDateTime> endAt = cb.coalesce(root.get("endAt"), root.get("startAt"));
                predicates.add(cb.lessThan(root.get("startAt"), nextMonthStart));
                predicates.add(cb.greaterThanOrEqualTo(endAt, monthStart));
            }
            if (condition.accessibleOnly()) {
                Subquery<Long> facility = query.subquery(Long.class);
                Root<VenueFacility> facilityRoot = facility.from(VenueFacility.class);
                facility.select(facilityRoot.get("id")).where(
                        cb.equal(facilityRoot.get("venue"), venue),
                        cb.equal(facilityRoot.get("status"), FacilityStatus.YES),
                        facilityRoot.get("facilityType").in(ACCESSIBILITY_FACILITIES));
                predicates.add(cb.exists(facility));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
