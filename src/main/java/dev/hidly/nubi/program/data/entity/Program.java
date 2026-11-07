package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import dev.hidly.nubi.program.data.enums.ProgramStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// ponytail: raw_notice_id, venue_id 연관관계는 RawNotice·Venue 엔티티가 생길 때 추가
@Getter
@Entity
@Table(name = "program")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Program extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_organization_id")
    private Organization hostOrganization;

    @Column(length = 100)
    private String hostName;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProgramCategory category;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ApplyMethod applyMethod;

    @Column(length = 300)
    private String applyUrl;

    @Column(length = 20)
    private String contactPhone;

    private LocalDateTime deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProgramStatus status;

    private LocalDateTime deletedAt;

    @Builder
    private Program(Organization hostOrganization, String hostName, String title, ProgramCategory category,
                    String description, LocalDateTime startAt, LocalDateTime endAt, Integer capacity,
                    ApplyMethod applyMethod, String applyUrl, String contactPhone, LocalDateTime deadlineAt,
                    ProgramStatus status) {
        this.hostOrganization = hostOrganization;
        this.hostName = hostName;
        this.title = title;
        this.category = category;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
        this.applyMethod = applyMethod;
        this.applyUrl = applyUrl;
        this.contactPhone = contactPhone;
        this.deadlineAt = deadlineAt;
        this.status = status == null ? ProgramStatus.DRAFT : status;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isHostedBy(Long organizationId) {
        return hostOrganization != null && hostOrganization.getId().equals(organizationId);
    }
}
