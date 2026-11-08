package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.ProgramCondition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProgramConditionRepository extends JpaRepository<ProgramCondition, Long> {
    List<ProgramCondition> findAllByProgramIdOrderByIdAsc(Long programId);

    @EntityGraph(attributePaths = "supportTag")
    List<ProgramCondition> findAllByProgramIdIn(Collection<Long> programIds);
}
