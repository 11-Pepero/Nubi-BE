package dev.hidly.nubi.support.data.repository;

import dev.hidly.nubi.support.data.entity.CarePerson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CarePersonRepository extends JpaRepository<CarePerson, Long> {
    @Query("""
            select distinct c from CarePerson c
            left join fetch c.group
            left join fetch c.items i
            left join fetch i.supportTag
            where c.organization.id = :organizationId and c.deletedAt is null
            order by c.id
            """)
    List<CarePerson> findActiveWithItems(@Param("organizationId") Long organizationId);

    @Query("""
            select c from CarePerson c
            left join fetch c.group
            left join fetch c.items i
            left join fetch i.supportTag
            where c.id = :careId and c.deletedAt is null
            """)
    Optional<CarePerson> findActiveWithItemsById(@Param("careId") Long careId);

    @Query("""
            select c.group.id, count(c) from CarePerson c
            where c.group.id in :groupIds and c.deletedAt is null
            group by c.group.id
            """)
    List<Object[]> countActiveByGroupIds(@Param("groupIds") Collection<Long> groupIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CarePerson c set c.group = null where c.group.id = :groupId")
    int clearGroup(@Param("groupId") Long groupId);
}
