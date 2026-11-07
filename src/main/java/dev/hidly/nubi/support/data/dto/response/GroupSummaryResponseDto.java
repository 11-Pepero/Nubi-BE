package dev.hidly.nubi.support.data.dto.response;

import java.util.List;

public record GroupSummaryResponseDto(int groupCount, int memberCount, List<SupportCountResponseDto> supports) {
}
