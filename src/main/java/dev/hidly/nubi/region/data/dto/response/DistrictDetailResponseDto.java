package dev.hidly.nubi.region.data.dto.response;

import java.time.YearMonth;
import java.util.List;

// emds는 참여 기회가 적은 순: 프로그램 수가 적은 곳부터, 같으면 시설이 많은 곳부터
public record DistrictDetailResponseDto(YearMonth month, DistrictStatResponseDto summary, List<EmdStatResponseDto> emds) {
}
