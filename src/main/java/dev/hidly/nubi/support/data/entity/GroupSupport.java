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
@Table(name = "group_support")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupSupport extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private SupportGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_tag_id", nullable = false)
    private SupportTag supportTag;

    private Integer quantity;

    @Column(length = 10)
    private String ratio;

    @Column(length = 200)
    private String note;

    @Builder
    private GroupSupport(SupportTag supportTag, Integer quantity, String ratio, String note) {
        this.supportTag = supportTag;
        this.quantity = quantity;
        this.ratio = ratio;
        this.note = note;
    }

    void assignGroup(SupportGroup group) {
        this.group = group;
    }
}
