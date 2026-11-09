package dev.hidly.nubi.review.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.review.data.enums.CheckAnswer;
import dev.hidly.nubi.support.data.entity.SupportTag;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지원 항목별로 공고에 적힌 대로였는지. 다음 기관의 참여 판정(프로그램 조건)에 반영된다.
@Getter
@Entity
@Table(name = "review_check", uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "support_tag_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCheck extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "support_tag_id", nullable = false)
    private SupportTag supportTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CheckAnswer answer;

    @Builder
    private ReviewCheck(SupportTag supportTag, CheckAnswer answer) {
        this.supportTag = supportTag;
        this.answer = answer;
    }

    void changeAnswer(CheckAnswer answer) {
        this.answer = answer;
    }

    void assignReview(Review review) {
        this.review = review;
    }
}
