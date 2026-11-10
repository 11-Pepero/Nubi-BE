package dev.hidly.nubi.document.data.entity;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.document.data.enums.DocumentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신청마다 종류별로 한 장. "다시 쓰기"를 하면 내용만 바꾼다.
@Getter
@Entity
@Table(name = "application_document", uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationDocument extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DocumentType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder
    private ApplicationDocument(Application application, DocumentType type, String content) {
        this.application = application;
        this.type = type;
        this.content = content;
    }

    public void rewrite(String content) {
        this.content = content;
    }
}
