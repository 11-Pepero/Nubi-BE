package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.ProgramCondition;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProgramConditionRepository extends JpaRepository<ProgramCondition, Long> {
    List<ProgramCondition> findAllByProgramIdOrderByIdAsc(Long programId);

    List<ProgramCondition> findAllByProgramIdAndSource(Long programId, ConditionSource source);

    @EntityGraph(attributePaths = "supportTag")
    List<ProgramCondition> findAllByProgramIdIn(Collection<Long> programIds);
}
