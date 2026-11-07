package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
