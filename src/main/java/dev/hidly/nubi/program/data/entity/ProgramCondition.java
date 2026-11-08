package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.program.data.enums.ConditionSource;
import dev.hidly.nubi.program.data.enums.ConditionStatus;
import dev.hidly.nubi.support.data.entity.SupportTag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "program_condition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramCondition extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_tag_id", nullable = false)
    private SupportTag supportTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ConditionStatus status;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConditionSource source;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @Builder
    private ProgramCondition(Program program, SupportTag supportTag, ConditionStatus status, String evidence,
                             ConditionSource source, BigDecimal confidence) {
        this.program = program;
        this.supportTag = supportTag;
        this.status = status == null ? ConditionStatus.UNKNOWN : status;
        this.evidence = evidence;
        this.source = source;
        this.confidence = confidence;
    }
}
