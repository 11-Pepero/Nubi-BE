package dev.hidly.nubi.review.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "review_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(nullable = false, length = 300)
    private String url;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private ReviewImage(Review review, String url, int sortOrder) {
        this.review = review;
        this.url = url;
        this.sortOrder = sortOrder;
    }
}
