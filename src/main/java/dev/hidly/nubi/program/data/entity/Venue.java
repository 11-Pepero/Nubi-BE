package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.enums.District;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Getter
@Entity
@Table(name = "venue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private District district;

    @Column(length = 10)
    private String emdCode;

    @Column(columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Builder
    private Venue(String name, String address, District district, String emdCode, Point location) {
        this.name = name;
        this.address = address;
        this.district = district;
        this.emdCode = emdCode;
        this.location = location;
    }

    public void fillLocation(String address, District district, Point location) {
        if (this.address == null) {
            this.address = address;
        }
        if (this.district == null) {
            this.district = district;
        }
        if (this.location == null) {
            this.location = location;
        }
    }
}
