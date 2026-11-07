package dev.hidly.nubi.support.data.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareItemRequestDto {
    private Long supportTagId;

    @Size(max = 200)
    private String content;

    @JsonIgnore
    @AssertTrue(message = "supportTagId와 content 중 하나는 있어야 합니다.")
    public boolean isTagOrContentPresent() {
        return supportTagId != null || (content != null && !content.isBlank());
    }
}
