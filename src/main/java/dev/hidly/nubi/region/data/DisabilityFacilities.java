package dev.hidly.nubi.region.data;

import dev.hidly.nubi.organization.data.enums.District;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

// 용인시 장애인복지시설 43곳(공공데이터 2026-05-07). 좌표와 행정동은 카카오로 한 번 조회해 CSV에 저장해 두었다.
// 공동생활가정·거주시설은 이용자가 사는 집이라 CSV에서 이름을 감추고 좌표를 약 100m 단위로 흐렸고, 주소·전화번호는 저장하지 않는다.
// 바뀔 일이 거의 없어 테이블 대신 시작할 때 메모리에 올린다.
@Component
public class DisabilityFacilities {
    public record Facility(String name, String facilityType, District district, String emdCode, String emdName,
                           double latitude, double longitude) {
    }

    private final List<Facility> facilities;

    public DisabilityFacilities() {
        try {
            String csv = new ClassPathResource("data/region/disability_facilities.csv").getContentAsString(StandardCharsets.UTF_8);
            // 값에 쉼표가 없도록 만든 파일이라 단순 split으로 읽는다
            this.facilities = csv.lines().skip(1).filter(line -> !line.isBlank()).map(line -> line.split(",", -1))
                    .map(c -> new Facility(c[0], c[1], District.valueOf(c[2]), c[3], c[4],
                            Double.parseDouble(c[5]), Double.parseDouble(c[6])))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Facility> all() {
        return facilities;
    }
}
