package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.ProgramImage;

public record ProgramImageResponseDto(Long imageId, String url, int sortOrder) {
    public static ProgramImageResponseDto from(ProgramImage image) {
        return new ProgramImageResponseDto(image.getId(), image.getUrl(), image.getSortOrder());
    }
}
