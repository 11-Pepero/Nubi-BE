package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Entity
@Table(name = "raw_notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RawNotice extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String dataset;

    @Column(nullable = false, unique = true, length = 50)
    private String externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> rawJson;

    @Column(nullable = false)
    private LocalDateTime collectedAt;

    @Builder
    private RawNotice(String dataset, String externalId, Map<String, Object> rawJson) {
        this.dataset = dataset;
        this.externalId = externalId;
        this.rawJson = rawJson;
        this.collectedAt = LocalDateTime.now();
    }
}
