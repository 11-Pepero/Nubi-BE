package dev.hidly.nubi.program.data.repository;

import dev.hidly.nubi.program.data.entity.RawNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface RawNoticeRepository extends JpaRepository<RawNotice, Long> {
    @Query("select r.externalId from RawNotice r where r.externalId in :externalIds")
    Set<String> findExistingExternalIds(@Param("externalIds") Collection<String> externalIds);

    boolean existsByExternalId(String externalId);
}
