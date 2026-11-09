package dev.hidly.nubi.review.data.entity;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.review.data.enums.ReviewReaction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 신청 하나에 후기 하나. 목록 조회가 많아 프로그램·작성 기관을 같이 들고 있는다.
@Getter
@Entity
@Table(name = "review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_org_id", nullable = false)
    private Organization authorOrganization;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    // 어떤 점이 달랐나요?
    @Column(length = 500)
    private String mismatchNote;

    @Column(nullable = false)
    private boolean isPublic;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReviewCheck> checks = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "review_reaction", joinColumns = @JoinColumn(name = "review_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "reaction"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "reaction", nullable = false, length = 20)
    private Set<ReviewReaction> reactions = new LinkedHashSet<>();

    @Builder
    private Review(Application application, int rating, String content, String mismatchNote, boolean isPublic,
                   Set<ReviewReaction> reactions) {
        this.application = application;
        this.program = application.getProgram();
        this.authorOrganization = application.getApplicantOrganization();
        this.rating = rating;
        this.content = content;
        this.mismatchNote = mismatchNote;
        this.isPublic = isPublic;
        if (reactions != null) {
            this.reactions.addAll(reactions);
        }
    }

    public void addCheck(ReviewCheck check) {
        check.assignReview(this);
        checks.add(check);
    }
}
