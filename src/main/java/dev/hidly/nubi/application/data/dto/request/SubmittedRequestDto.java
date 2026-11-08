package dev.hidly.nubi.application.data.dto.request;

import dev.hidly.nubi.program.data.enums.ApplyMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmittedRequestDto {
    // 실제로 접수한 방법(ONLINE, PHONE, PAPER). 비우면 공고의 접수 방법 그대로
    private ApplyMethod channel;

    // 접수한 시각. 비우면 지금
    private LocalDateTime submittedAt;
}
