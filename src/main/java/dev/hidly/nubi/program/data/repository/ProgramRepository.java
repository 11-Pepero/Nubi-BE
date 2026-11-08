package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {
    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    Optional<Program> findWithVenueById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Program p where p.id = :id")
    Optional<Program> findLockedById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    Page<Program> findAll(Specification<Program> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"venue", "hostOrganization"})
    @Query("""
            select p from Program p
            where p.status = dev.hidly.nubi.program.data.enums.ProgramStatus.OPEN and p.deletedAt is null
              and (coalesce(p.endAt, p.startAt) is null or coalesce(p.endAt, p.startAt) >= :now)
            """)
    List<Program> findJudgeable(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Program p set p.status = dev.hidly.nubi.program.data.enums.ProgramStatus.ENDED
            where p.status in (dev.hidly.nubi.program.data.enums.ProgramStatus.OPEN, dev.hidly.nubi.program.data.enums.ProgramStatus.CLOSED)
              and coalesce(p.endAt, p.startAt) < :now
            """)
    int endFinished(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Program p set p.status = dev.hidly.nubi.program.data.enums.ProgramStatus.CLOSED
            where p.status = dev.hidly.nubi.program.data.enums.ProgramStatus.OPEN and p.deadlineAt < :now
            """)
    int closePastDeadline(@Param("now") LocalDateTime now);
}
