package dev.hidly.nubi.review.data.dto.request;

import dev.hidly.nubi.review.data.enums.CheckAnswer;
import dev.hidly.nubi.review.data.enums.ReviewReaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequestDto {
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Valid
    private List<@NotNull @Valid Check> checks;

    @Size(max = 500)
    private String mismatchNote;

    private Set<@NotNull ReviewReaction> reactions;

    @Size(max = 2000)
    private String content;

    // 비우면 공개(다른 기관에도 보여요)
    private Boolean isPublic;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Check {
        @NotNull
        private Long supportTagId;

        @NotNull
        private CheckAnswer answer;
    }

    public List<Check> checksOrEmpty() {
        return checks == null ? List.of() : checks;
    }

    public Set<ReviewReaction> reactionsOrEmpty() {
        return reactions == null ? Set.of() : reactions;
    }

    public boolean isPublicOrDefault() {
        return isPublic == null || isPublic;
    }
}
