package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {
    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    Optional<Program> findWithVenueById(Long id);

    @Override
    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    Page<Program> findAll(Specification<Program> spec, Pageable pageable);
}
