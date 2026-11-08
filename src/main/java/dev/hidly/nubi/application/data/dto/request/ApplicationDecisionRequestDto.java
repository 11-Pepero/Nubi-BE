package dev.hidly.nubi.application.data.dto.request;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDecisionRequestDto {
    // CONFIRMED(확정), WAITING(대기), REJECTED(거절)
    @NotNull
    private ApplicationStatus status;

    // ERD에 사유 컬럼이 없어 주최 기관 메시지로 남긴다.
    @Size(max = 1000)
    private String reason;
}
