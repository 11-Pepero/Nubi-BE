package dev.hidly.nubi.region.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.enums.District;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 구 단위 행은 emd_code가 비어 있다. 프로그램 수는 공고가 계속 들어와서 저장하지 않고 조회할 때 센다.
@Getter
@Entity
@Table(name = "region_stat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionStat extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private District district;

    @Column(length = 10)
    private String emdCode;

    @Column(length = 20)
    private String emdName;

    @Column(nullable = false)
    private int facilityCount;

    @Column(nullable = false)
    private int registeredDisabled;

    private LocalDate baseDate;

    @Builder
    private RegionStat(District district, String emdCode, String emdName, int facilityCount, int registeredDisabled,
                       LocalDate baseDate) {
        this.district = district;
        this.emdCode = emdCode;
        this.emdName = emdName;
        this.facilityCount = facilityCount;
        this.registeredDisabled = registeredDisabled;
        this.baseDate = baseDate;
    }
}
