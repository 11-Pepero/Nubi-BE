package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.VenueFacility;
import dev.hidly.nubi.program.data.enums.FacilitySource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VenueFacilityRepository extends JpaRepository<VenueFacility, Long> {
    List<VenueFacility> findAllByVenueId(Long venueId);

    List<VenueFacility> findAllByVenueIdIn(Collection<Long> venueIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from VenueFacility f where f.venue.id = :venueId and f.source = :source")
    int deleteAllByVenueIdAndSource(@Param("venueId") Long venueId, @Param("source") FacilitySource source);
}
