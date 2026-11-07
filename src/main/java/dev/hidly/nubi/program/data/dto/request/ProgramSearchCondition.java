package dev.hidly.nubi.program.data.dto.request;

import dev.hidly.nubi.organization.data.enums.District;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.program.data.enums.ProgramCategory;

import java.time.YearMonth;

public record ProgramSearchCondition(
        String keyword,
        ProgramCategory category,
        District district,
        YearMonth month,
        ApplyMethod applyMethod,
        boolean accessibleOnly
) {
}
