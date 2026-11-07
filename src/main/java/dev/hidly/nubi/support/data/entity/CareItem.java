package dev.hidly.nubi.support.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "care_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareItem extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_person_id", nullable = false)
    private CarePerson carePerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "support_tag_id")
    private SupportTag supportTag;

    @Column(length = 200)
    private String content;

    @Builder
    private CareItem(SupportTag supportTag, String content) {
        this.supportTag = supportTag;
        this.content = content;
    }

    void assignCarePerson(CarePerson carePerson) {
        this.carePerson = carePerson;
    }
}
