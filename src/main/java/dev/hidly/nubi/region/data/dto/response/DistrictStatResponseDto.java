package dev.hidly.nubi.region.data.dto.response;

import dev.hidly.nubi.organization.data.enums.District;

public record DistrictStatResponseDto(District district,
                                      String name,
                                      int facilityCount,
                                      int registeredDisabled,
                                      double facilitiesPerThousand,
                                      long programCount,
                                      long accessibleProgramCount,
                                      Double programsPerFacility) {
}
