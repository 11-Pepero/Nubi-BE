package dev.hidly.nubi.support.data.repository;

import dev.hidly.nubi.support.data.entity.SupportGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupportGroupRepository extends JpaRepository<SupportGroup, Long> {
    @Query("""
            select distinct g from SupportGroup g
            left join fetch g.supports s
            left join fetch s.supportTag
            where g.organization.id = :organizationId and g.deletedAt is null
            order by g.id
            """)
    List<SupportGroup> findActiveWithSupports(@Param("organizationId") Long organizationId);

    @Query("""
            select g from SupportGroup g
            left join fetch g.supports s
            left join fetch s.supportTag
            where g.id = :groupId and g.deletedAt is null
            """)
    Optional<SupportGroup> findActiveWithSupportsById(@Param("groupId") Long groupId);
}
