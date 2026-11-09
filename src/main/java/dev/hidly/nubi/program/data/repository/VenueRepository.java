package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Venue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    Optional<Venue> findFirstByNameOrderByIdAsc(String name);

    @Query("select v from Venue v where v.location is null order by v.locationCheckedAt asc nulls first, v.id asc")
    List<Venue> findWithoutLocation(Pageable pageable);

    List<Venue> findTop100ByLocationIsNotNullAndEmdCodeIsNullOrderByIdAsc();
}
