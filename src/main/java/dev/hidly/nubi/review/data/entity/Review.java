package dev.hidly.nubi.review.data.entity;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.review.data.enums.CheckAnswer;
import dev.hidly.nubi.review.data.enums.ReviewReaction;
import dev.hidly.nubi.support.data.entity.SupportTag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public void update(int rating, String content, String mismatchNote, boolean isPublic, Set<ReviewReaction> reactions) {
        this.rating = rating;
        this.content = content;
        this.mismatchNote = mismatchNote;
        this.isPublic = isPublic;
        this.reactions.clear();
        this.reactions.addAll(reactions);
    }

    // 확인 항목을 통째로 바꾼다. 같은 지원 항목은 행을 지우고 다시 넣지 않고 답만 고친다
    // (지우기보다 넣기가 먼저 실행돼 (review_id, support_tag_id) 유니크에 걸리기 때문).
    public void replaceChecks(Map<SupportTag, CheckAnswer> answers) {
        Map<Long, CheckAnswer> byTagId = answers.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getId(), Map.Entry::getValue));
        checks.removeIf(check -> !byTagId.containsKey(check.getSupportTag().getId()));
        Set<Long> kept = new HashSet<>();
        for (ReviewCheck check : checks) {
            check.changeAnswer(byTagId.get(check.getSupportTag().getId()));
            kept.add(check.getSupportTag().getId());
        }
        answers.forEach((tag, answer) -> {
            if (!kept.contains(tag.getId())) {
                addCheck(ReviewCheck.builder().supportTag(tag).answer(answer).build());
            }
        });
    }
}
