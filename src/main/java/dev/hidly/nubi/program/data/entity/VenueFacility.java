package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.program.data.enums.FacilitySource;
import dev.hidly.nubi.program.data.enums.FacilityStatus;
import dev.hidly.nubi.program.data.enums.FacilityType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "venue_facility")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueFacility extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FacilityType facilityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FacilityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacilitySource source;

    private LocalDateTime verifiedAt;

    @Builder
    private VenueFacility(Venue venue, FacilityType facilityType, FacilityStatus status, FacilitySource source,
                          LocalDateTime verifiedAt) {
        this.venue = venue;
        this.facilityType = facilityType;
        this.status = status;
        this.source = source;
        this.verifiedAt = verifiedAt;
    }
}
