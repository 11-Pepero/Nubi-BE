package dev.hidly.nubi.region.data.dto.response;

public record EmdStatResponseDto(String emdCode,
                                 String emdName,
                                 int facilityCount,
                                 int registeredDisabled,
                                 long programCount,
                                 long accessibleProgramCount) {
}
