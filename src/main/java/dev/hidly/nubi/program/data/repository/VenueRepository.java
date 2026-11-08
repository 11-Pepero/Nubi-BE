package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    Optional<Venue> findFirstByNameOrderByIdAsc(String name);

    List<Venue> findTop50ByLocationIsNullOrderByIdAsc();
}
