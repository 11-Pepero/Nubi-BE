package dev.hidly.nubi.application.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 문의 보내기(API-33), 답장 보내기(API-47)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequestDto {
    @NotBlank
    @Size(max = 1000)
    private String body;
}
