package dev.hidly.nubi.program.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "program_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramImage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false, length = 300)
    private String url;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private ProgramImage(Program program, String url, int sortOrder) {
        this.program = program;
        this.url = url;
        this.sortOrder = sortOrder;
    }
}
