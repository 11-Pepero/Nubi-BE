package dev.hidly.nubi.support.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.support.data.enums.SupportCategory;
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

@Getter
@Entity
@Table(name = "support_tag")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportTag extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportCategory category;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 40)
    private String label;

    @Builder
    private SupportTag(SupportCategory category, String code, String label) {
        this.category = category;
        this.code = code;
        this.label = label;
    }

    public boolean update(SupportCategory category, String label) {
        if (this.category == category && this.label.equals(label)) {
            return false;
        }
        this.category = category;
        this.label = label;
        return true;
    }
}
