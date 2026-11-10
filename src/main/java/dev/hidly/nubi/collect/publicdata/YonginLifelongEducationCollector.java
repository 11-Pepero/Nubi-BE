package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// 용인시 통합 홈페이지 평생교육관련 정보 (공공데이터포털 15154212). 원문에 번호가 없어 교육명과 날짜로 알아본다.
@Component
public class YonginLifelongEducationCollector extends YonginOdcloudCollector {
    public static final String DATASET = "YONGIN_LIFELONG_EDUCATION";
    private static final String URL = "https://api.odcloud.kr/api/15154212/v1/uddi:67ed6dd2-30c2-4c6a-8f69-f6fc1128cbdf";

    public YonginLifelongEducationCollector(RestClient collectRestClient,
                                            @Value("${external.data-go-kr.service-key:}") String serviceKey) {
        super(collectRestClient, serviceKey, URL);
    }

    @Override
    public String dataset() {
        return DATASET;
    }

    @Override
    protected NoticeData toNotice(Map<String, Object> row) {
        String title = text(row.get("평생교육명"));
        LocalDate start = date(row.get("평생교육시작일"));
        if (title == null || start == null) {
            return null;
        }
        LocalDate end = Objects.requireNonNullElse(date(row.get("평생교육종료일")), start);
        String externalId = DATASET + "-" + Integer.toHexString(Objects.hash(title, start, text(row.get("강의시작시간")),
                text(row.get("평생교육장소"))));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("시간", join(text(row.get("강의시작시간")), text(row.get("강의종료시간"))));
        fields.put("수강 신청", join(text(row.get("수강신청기간시작일")), text(row.get("수강신청기간종료일"))));
        fields.put("정원", text(row.get("교육정원수")));
        fields.put("내용", text(row.get("평생교육내용")));
        return new NoticeData(DATASET, externalId, row, title, ProgramCategory.EDUCATION,
                at(start, time(row.get("강의시작시간")), LocalTime.MIDNIGHT),
                at(end, time(row.get("강의종료시간")), LocalTime.of(23, 59)),
                text(row.get("평생교육장소")), null, null, description(fields), null);
    }

    private static String join(String from, String to) {
        if (from == null && to == null) {
            return null;
        }
        return Objects.toString(from, "") + " ~ " + Objects.toString(to, "");
    }
}
