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

// 용인시 통합 홈페이지 예절교육관프로그램 (공공데이터포털 15155465). 예절·인성 체험 교실.
@Component
public class YonginEtiquetteEducationCollector extends YonginOdcloudCollector {
    public static final String DATASET = "YONGIN_ETIQUETTE_EDUCATION";
    private static final String URL = "https://api.odcloud.kr/api/15155465/v1/uddi:6b5549ba-5172-4dd3-8939-b366cea204fb";

    public YonginEtiquetteEducationCollector(RestClient collectRestClient,
                                             @Value("${external.data-go-kr.service-key:}") String serviceKey) {
        super(collectRestClient, serviceKey, URL);
    }

    @Override
    public String dataset() {
        return DATASET;
    }

    @Override
    protected NoticeData toNotice(Map<String, Object> row) {
        String id = text(row.get("프로그램일련번호"));
        String title = text(row.get("프로그램강좌명"));
        LocalDate start = date(row.get("교육기간시작일"));
        if (id == null || title == null || start == null) {
            return null;
        }
        LocalDate end = Objects.requireNonNullElse(date(row.get("교육기간종료일")), start);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("접수 기간", text(row.get("접수기간시작일")) == null ? null
                : text(row.get("접수기간시작일")) + " ~ " + Objects.toString(text(row.get("접수기간종료일")), ""));
        fields.put("정원", text(row.get("정원수")));
        fields.put("수강 비용", text(row.get("수강비용")));
        fields.put("강사", text(row.get("강사명")));
        fields.put("내용", text(row.get("수강내용")));
        return new NoticeData(DATASET, DATASET + "-" + id, row, title, ProgramCategory.EDUCATION,
                at(start, null, LocalTime.MIDNIGHT), at(end, null, LocalTime.of(23, 59)),
                text(row.get("교육장소")), null, null, description(fields), null);
    }
}
