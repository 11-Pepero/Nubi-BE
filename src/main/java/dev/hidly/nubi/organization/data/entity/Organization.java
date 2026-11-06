package dev.hidly.nubi.organization.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.organization.data.enums.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "organization")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrganizationType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private District district;

    @Column(length = 200)
    private String address;

    @Column(columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(length = 20)
    private String phone;

    private LocalDateTime deletedAt;

    @Builder
    private Organization(String name, OrganizationType type, District district, String address, String phone) {
        this.name = name;
        this.type = type;
        this.district = district;
        this.address = address;
        this.phone = phone;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
