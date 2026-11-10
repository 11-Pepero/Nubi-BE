package dev.hidly.nubi.collect.facility;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 한국사회보장정보원 장애인편의시설 현황 (공공데이터포털 15092317)
// 목록(getDisConvFaclList)으로 용인시 시설을 받고, 시설마다 설치된 편의시설(getFacInfoOpenApiJpEvalInfoList)을 읽는다.
@Slf4j
@Component
public class AccessibilityFacilityClient {
    private static final String BASE = "https://apis.data.go.kr/B554287/DisabledPersonConvenientFacility";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 10;

    // 목록의 시설 한 곳. address는 소재지 주소(도로명 또는 지번)
    public record Place(String id, String name, String type, String address, Double latitude, Double longitude) {
    }

    private final RestClient collectRestClient;
    private final String serviceKey;

    public AccessibilityFacilityClient(RestClient collectRestClient,
                                       @Value("${external.data-go-kr.service-key:}") String serviceKey) {
        this.collectRestClient = collectRestClient;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    public boolean isEnabled() {
        return !serviceKey.isBlank();
    }

    public List<Place> findPlaces(String sido, String sigungu) {
        List<Place> places = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Document document = fetch("/getDisConvFaclList?pageNo=" + page + "&numOfRows=" + PAGE_SIZE
                    + "&siDoNm=" + encode(sido) + "&cggNm=" + encode(sigungu));
            if (document == null) {
                break;
            }
            NodeList items = document.getElementsByTagName("servList");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String id = text(item, "wfcltId");
                if (id != null) {
                    places.add(new Place(id, text(item, "faclNm"), text(item, "faclTyCd"), text(item, "lcMnad"),
                            number(text(item, "faclLat")), number(text(item, "faclLng"))));
                }
            }
            long total = Optional.ofNullable(number(firstText(document, "totalCount"))).map(Double::longValue).orElse(0L);
            if (items.getLength() == 0 || (long) page * PAGE_SIZE >= total) {
                break;
            }
        }
        return places;
    }

    // "승강기, 장애인사용가능화장실, 주출입구 높이차이 제거"처럼 설치된 편의시설 이름들
    public List<String> findInstalledItems(String placeId) {
        Document document = fetch("/getFacInfoOpenApiJpEvalInfoList?wfcltId=" + encode(placeId));
        String evalInfo = document == null ? null : firstText(document, "evalInfo");
        if (evalInfo == null) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String item : evalInfo.split(",")) {
            if (!item.isBlank()) {
                items.add(item.trim());
            }
        }
        return items;
    }

    private Document fetch(String path) {
        String encodedKey = URLEncoder.encode(URLDecoder.decode(serviceKey, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        try {
            byte[] body = collectRestClient.get()
                    .uri(URI.create(BASE + path + "&serviceKey=" + encodedKey))
                    .retrieve()
                    .body(byte[].class);
            return body == null ? null : parse(body);
        } catch (RuntimeException e) {
            log.warn("[AccessibilityFacilityClient] 요청 실패: {}", summarize(e));
            return null;
        }
    }

    static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            log.warn("[AccessibilityFacilityClient] 응답을 읽지 못함: {}", e.getMessage());
            return null;
        }
    }

    private static String firstText(Document document, String tag) {
        NodeList nodes = document.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : blankToNull(nodes.item(0).getTextContent());
    }

    private static String text(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        Node node = nodes.getLength() == 0 ? null : nodes.item(0);
        return node == null ? null : blankToNull(node.getTextContent());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Double number(String value) {
        try {
            return value == null ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String summarize(RuntimeException e) {
        String message = String.valueOf(e.getMessage()).replaceAll("serviceKey=[^&\"\\s]*", "serviceKey=***").replaceAll("\\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
