package dev.hidly.nubi.region.data.repository;

import dev.hidly.nubi.program.data.enums.FacilityType;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import dev.hidly.nubi.region.data.entity.RegionStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface RegionStatRepository extends JpaRepository<RegionStat, Long> {
    List<RegionStat> findAllByEmdCodeIsNull();

    // [from, to) 기간에 열리는 프로그램 수를 장소의 구별로 센다
    @Query("""
            select v.district, count(p) from Program p join p.venue v
            where p.deletedAt is null and p.status in :statuses and v.district is not null
              and p.startAt < :to and coalesce(p.endAt, p.startAt) >= :from
            group by v.district
            """)
    List<Object[]> countProgramsByDistrict(@Param("statuses") Collection<ProgramStatus> statuses,
                                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // 위와 같고, 장소에 접근성 편의시설이 있다고 확인된 프로그램만 센다 (프로그램 탐색 accessibleOnly와 같은 기준)
    @Query("""
            select v.district, count(p) from Program p join p.venue v
            where p.deletedAt is null and p.status in :statuses and v.district is not null
              and p.startAt < :to and coalesce(p.endAt, p.startAt) >= :from
              and exists (select 1 from VenueFacility f where f.venue = v
                          and f.status = dev.hidly.nubi.program.data.enums.FacilityStatus.YES and f.facilityType in :types)
            group by v.district
            """)
    List<Object[]> countAccessibleProgramsByDistrict(@Param("statuses") Collection<ProgramStatus> statuses,
                                                     @Param("types") Collection<FacilityType> types,
                                                     @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
