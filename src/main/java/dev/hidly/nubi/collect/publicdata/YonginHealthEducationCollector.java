package dev.hidly.nubi.collect.publicdata;

import dev.hidly.nubi.collect.NoticeData;
import dev.hidly.nubi.program.data.enums.ProgramCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 용인시 통합 홈페이지 보건교육관리 현황 (공공데이터포털 15155188). 구 보건소 3곳의 건강교실 모집 공고.
@Component
public class YonginHealthEducationCollector extends YonginOdcloudCollector {
    public static final String DATASET = "YONGIN_HEALTH_EDUCATION";
    private static final String URL = "https://api.odcloud.kr/api/15155188/v1/uddi:1ea44775-d82e-4086-b6a4-a2bdc3221cf1";
    private static final Map<String, String> HEALTH_CENTERS = Map.of(
            "4146100000", "처인구보건소",
            "4146300000", "기흥구보건소",
            "4146500000", "수지구보건소");

    public YonginHealthEducationCollector(RestClient collectRestClient,
                                          @Value("${external.data-go-kr.service-key:}") String serviceKey) {
        super(collectRestClient, serviceKey, URL);
    }

    @Override
    public String dataset() {
        return DATASET;
    }

    @Override
    protected NoticeData toNotice(Map<String, Object> row) {
        String id = text(row.get("교육일련번호"));
        String title = text(row.get("보건교육제목"));
        LocalDate applyStart = date(row.get("보건교육접수시작일"));
        LocalDate applyEnd = date(row.get("보건교육접수종료일"));
        if (id == null || title == null) {
            return null;
        }
        String period = text(row.get("보건교육기간"));
        Integer baseYear = applyStart != null ? Integer.valueOf(applyStart.getYear()) : applyEnd == null ? null : applyEnd.getYear();
        List<LocalDate> dates = datesIn(period, baseYear);
        // 교육 기간을 못 읽으면 접수 마감일을 행사 날짜로 둔다 (끝난 공고를 끝난 상태로 두기 위해).
        LocalDate start = dates.isEmpty() ? (applyEnd != null ? applyEnd : applyStart) : dates.getFirst();
        LocalDate end = dates.size() > 1 ? dates.getLast() : start;
        if (start == null) {
            return null;
        }
        String center = HEALTH_CENTERS.get(text(row.get("보건소지역코드")));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("교육 기간", period);
        fields.put("접수 기간", applyStart == null && applyEnd == null ? null : applyStart + " ~ " + applyEnd);
        fields.put("정원", text(row.get("교육 인원수")));
        fields.put("내용", text(row.get("보건교육내용")));
        return new NoticeData(DATASET, DATASET + "-" + id, row, title, ProgramCategory.HEALTH,
                at(start, null, LocalTime.MIDNIGHT), at(end, null, LocalTime.of(23, 59)),
                center, null, null, description(fields), null);
    }
}
