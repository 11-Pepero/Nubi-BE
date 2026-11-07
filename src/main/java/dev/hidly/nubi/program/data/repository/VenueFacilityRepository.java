package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.VenueFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueFacilityRepository extends JpaRepository<VenueFacility, Long> {
    List<VenueFacility> findAllByVenueId(Long venueId);
}
