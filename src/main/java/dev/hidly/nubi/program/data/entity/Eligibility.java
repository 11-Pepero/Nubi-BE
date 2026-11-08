package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.program.data.enums.EligibilityResult;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "eligibility", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "program_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Eligibility extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private SupportGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EligibilityResult result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private EligibilityReasons reasons;

    @Column(nullable = false)
    private LocalDateTime computedAt;

    @Builder
    private Eligibility(SupportGroup group, Program program, EligibilityResult result, EligibilityReasons reasons) {
        this.group = group;
        this.program = program;
        this.result = result;
        this.reasons = reasons;
        this.computedAt = LocalDateTime.now();
    }

    public void update(EligibilityResult result, EligibilityReasons reasons) {
        this.result = result;
        this.reasons = reasons;
        this.computedAt = LocalDateTime.now();
    }
}
