package dev.hidly.nubi.support.data.repository;

import dev.hidly.nubi.support.data.entity.SupportTag;
import dev.hidly.nubi.support.data.enums.SupportCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTagRepository extends JpaRepository<SupportTag, Long> {
    List<SupportTag> findAllByOrderByIdAsc();

    List<SupportTag> findAllByCategoryOrderByIdAsc(SupportCategory category);

    boolean existsByCode(String code);
}
