package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    Optional<Venue> findFirstByNameOrderByIdAsc(String name);

    List<Venue> findAllByNameIn(Collection<String> names);

    List<Venue> findAllByAddressIsNotNull();

    @Query("select v from Venue v where v.location is null order by v.locationCheckedAt asc nulls first, v.id asc")
    List<Venue> findWithoutLocation(Pageable pageable);

    List<Venue> findTop100ByLocationIsNotNullAndEmdCodeIsNullOrderByIdAsc();

    @Query("""
            select v from Venue v
            where lower(v.name) like :pattern or lower(coalesce(v.address, '')) like :pattern
            """)
    Page<Venue> search(@Param("pattern") String pattern, Pageable pageable);
}
