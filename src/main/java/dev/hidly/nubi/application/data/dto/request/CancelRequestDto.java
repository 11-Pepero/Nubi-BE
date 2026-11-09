package dev.hidly.nubi.application.data.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신청 취소(API-32). ERD에 사유 컬럼이 없어 신청 기관 메시지로 남긴다.
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequestDto {
    @Size(max = 1000)
    private String reason;
}
