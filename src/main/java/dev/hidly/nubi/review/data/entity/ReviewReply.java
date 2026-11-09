package dev.hidly.nubi.review.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 주최 기관의 답글. 후기 하나에 답글 하나.
@Getter
@Entity
@Table(name = "review_reply")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReply extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 1000)
    private String content;

    @Builder
    private ReviewReply(Review review, Organization organization, String content) {
        this.review = review;
        this.organization = organization;
        this.content = content;
    }
}
