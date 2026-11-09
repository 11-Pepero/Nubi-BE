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
    private Organization(String name, OrganizationType type, District district, String address, Point location, String phone) {
        this.name = name;
        this.type = type;
        this.district = district;
        this.address = address;
        this.location = location;
        this.phone = phone;
    }

    // 내 기관 수정 (API-5). 보낸 값만 바꾸고, 주소가 바뀌면 구도 다시 정한다. 좌표는 그대로 둔다.
    public void update(String name, String address, String phone) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
            this.district = District.fromAddress(address).orElse(null);
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
