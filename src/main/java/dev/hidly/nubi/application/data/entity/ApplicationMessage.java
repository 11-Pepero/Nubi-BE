package dev.hidly.nubi.application.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신청 기관의 문의와 주최 기관의 답장
@Getter
@Entity
@Table(name = "application_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationMessage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_org_id", nullable = false)
    private Organization senderOrganization;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Builder
    private ApplicationMessage(Organization senderOrganization, String body) {
        this.senderOrganization = senderOrganization;
        this.body = body;
    }

    void assignApplication(Application application) {
        this.application = application;
    }
}
