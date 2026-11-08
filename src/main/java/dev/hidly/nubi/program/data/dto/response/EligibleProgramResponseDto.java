package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.program.data.entity.EligibilityReasons;
import dev.hidly.nubi.program.data.enums.EligibilityResult;
import dev.hidly.nubi.program.data.enums.ItemResult;

public record EligibleProgramResponseDto(
        ProgramSummaryResponseDto program,
        EligibilityResult result,
        long metCount,
        long missingCount,
        long unknownCount,
        long careCheckCount
) {
    public static EligibleProgramResponseDto of(ProgramSummaryResponseDto program, EligibilityResult result,
                                                EligibilityReasons reasons) {
        return new EligibleProgramResponseDto(program, result,
                reasons.count(ItemResult.MET), reasons.count(ItemResult.MISSING), reasons.count(ItemResult.UNKNOWN),
                reasons.careCheckCount());
    }
}
