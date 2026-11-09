package dev.hidly.nubi.application.data.dto.request;

import dev.hidly.nubi.application.data.enums.PrepStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 지원 준비 체크(API-46): PENDING(준비 전), READY(준비함), CHECK(확인 필요)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportPrepRequestDto {
    @NotNull
    private PrepStatus prepStatus;

    // 예: "앞줄 통로 쪽 2석"
    @Size(max = 200)
    private String hostNote;
}
