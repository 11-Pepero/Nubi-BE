package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.ProgramImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramImageRepository extends JpaRepository<ProgramImage, Long> {
    List<ProgramImage> findAllByProgramIdOrderBySortOrderAsc(Long programId);
}
