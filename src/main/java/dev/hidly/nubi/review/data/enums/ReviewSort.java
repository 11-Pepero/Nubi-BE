package dev.hidly.nubi.review.data.enums;

import org.springframework.data.domain.Sort;

// 받은 후기 정렬: 최신순 / 별점 낮은 순
public enum ReviewSort {
    LATEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    RATING_ASC(Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private final Sort sort;

    ReviewSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
