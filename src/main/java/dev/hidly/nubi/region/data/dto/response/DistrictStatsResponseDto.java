package dev.hidly.nubi.region.data.dto.response;

import java.time.YearMonth;
import java.util.List;

public record DistrictStatsResponseDto(YearMonth month, List<DistrictStatResponseDto> districts) {
}
