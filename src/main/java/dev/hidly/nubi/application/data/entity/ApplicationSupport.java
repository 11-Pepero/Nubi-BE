package dev.hidly.nubi.application.data.entity;

import dev.hidly.nubi.application.data.enums.PrepStatus;
import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.support.data.entity.SupportTag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신청할 때 그룹 공통 지원을 복사해 둔 스냅샷. 그룹을 고쳐도 바뀌지 않는다.
@Getter
@Entity
@Table(name = "application_support")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationSupport extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_tag_id", nullable = false)
    private SupportTag supportTag;

    // 비어 있으면 신청 인원 전체에 필요한 지원
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrepStatus prepStatus;

    @Column(length = 200)
    private String hostNote;

    @Builder
    private ApplicationSupport(SupportTag supportTag, Integer quantity) {
        this.supportTag = supportTag;
        this.quantity = quantity;
        this.prepStatus = PrepStatus.PENDING;
    }

    // 주최 기관의 지원 준비 체크 (화면 13). 메모를 비우면 지운다.
    public void updatePrep(PrepStatus prepStatus, String hostNote) {
        this.prepStatus = prepStatus;
        this.hostNote = hostNote == null || hostNote.isBlank() ? null : hostNote.trim();
    }

    void assignApplication(Application application) {
        this.application = application;
    }
}
