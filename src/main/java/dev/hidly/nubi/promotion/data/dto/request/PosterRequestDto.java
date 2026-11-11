package dev.hidly.nubi.promotion.data.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosterRequestDto {
    // 화면에서 보고 고친 쉬운 말 홍보문. 비우면 AI 없이 쓴 쉬운 말 초안을 넣는다.
    @Size(max = 1000)
    private String easy;
}
