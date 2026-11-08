package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {
    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    Optional<Program> findWithVenueById(Long id);

    @Override
    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    Page<Program> findAll(Specification<Program> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    List<Program> findAllByStatusAndDeletedAtIsNull(ProgramStatus status);

    @Query("select p from Program p where p.rawNotice is not null and p.status = :status and p.endAt < :now")
    List<Program> findCollectedEndedBefore(@Param("status") ProgramStatus status, @Param("now") LocalDateTime now);
}
