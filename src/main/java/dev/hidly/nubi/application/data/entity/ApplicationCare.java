package dev.hidly.nubi.application.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.support.data.entity.CarePerson;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신청할 때 함께 보낸 개별 케어. 별칭과 케어 내용을 복사해 둔다(실명·진단명은 저장하지 않음).
@Getter
@Entity
@Table(name = "application_care")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationCare extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_person_id")
    private CarePerson carePerson;

    @Column(nullable = false, length = 20)
    private String aliasSnapshot;

    @Column(columnDefinition = "TEXT")
    private String contentSnapshot;

    @Builder
    private ApplicationCare(CarePerson carePerson, String aliasSnapshot, String contentSnapshot) {
        this.carePerson = carePerson;
        this.aliasSnapshot = aliasSnapshot;
        this.contentSnapshot = contentSnapshot;
    }

    void assignApplication(Application application) {
        this.application = application;
    }
}
