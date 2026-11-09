package dev.hidly.nubi.review.data.dto.response;

import dev.hidly.nubi.review.data.entity.ReviewImage;

public record ReviewImageResponseDto(Long imageId, String url, int sortOrder) {
    public static ReviewImageResponseDto from(ReviewImage image) {
        return new ReviewImageResponseDto(image.getId(), image.getUrl(), image.getSortOrder());
    }
}
