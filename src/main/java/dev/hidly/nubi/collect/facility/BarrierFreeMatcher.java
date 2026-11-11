package dev.hidly.nubi.collect.facility;

import dev.hidly.nubi.collect.facility.BarrierFreeTourClient.Place;
import dev.hidly.nubi.program.data.enums.FacilityType;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// 우리 장소와 무장애 여행 정보의 시설을 잇고, 무장애 상세 문장을 FacilityType으로 바꾼다.
public final class BarrierFreeMatcher {
    private static final double NEAR_METERS = 300;
    // 이름이 겹치는지 볼 때 빼는 흔한 말. 빼지 않으면 "한국미술관"과 "벗이미술관"이 이어진다.
    private static final Pattern REGION = Pattern.compile("경기도|용인특례시|용인시|용인");
    private static final Pattern GENERIC = Pattern.compile("작은도서관|도서관|미술관|박물관|아트센터|센터|갤러리|기념|학교|문화|예술|홀");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s()·,\\-]");

    private BarrierFreeMatcher() {
    }

    // 이름이 겹치고, 도로명주소가 같거나 300m 안이면 잇는다. 여럿이면 가장 가까운 것. 좌표가 없으면 주소로만 본다.
    // 주소만 같은 것은 잇지 않는다. 시청(중부대로 1199)처럼 한 주소에 보건소·예절교육관·공연장이 함께 있는 곳이 있다.
    public static Optional<Place> match(String venueName, String venueAddress, Double latitude, Double longitude,
                                        Collection<Place> places) {
        Optional<String> venueRoad = AccessibilityFacilityMatcher.addressKey(venueAddress);
        return places.stream()
                .filter(place -> {
                    boolean sameRoad = venueRoad.isPresent()
                            && venueRoad.equals(AccessibilityFacilityMatcher.addressKey(place.address()));
                    boolean named = namesOverlap(venueName, place.title());
                    Double distance = distance(latitude, longitude, place);
                    return named && (sameRoad || (distance != null && distance <= NEAR_METERS));
                })
                .min(Comparator.comparingDouble(place -> Objects.requireNonNullElse(distance(latitude, longitude, place), 0.0)));
    }

    static boolean namesOverlap(String venueName, String placeName) {
        String venue = compact(venueName);
        String place = compact(placeName);
        String venueKey = GENERIC.matcher(venue).replaceAll("");
        String placeKey = GENERIC.matcher(place).replaceAll("");
        if (venueKey.length() < 2 || placeKey.length() < 2) {
            // "용인시박물관"처럼 흔한 말밖에 안 남으면 이름이 같을 때만
            return !venue.isEmpty() && venue.equals(place);
        }
        return venueKey.contains(placeKey) || placeKey.contains(venueKey);
    }

    // 무장애 상세 문장 → 편의시설 종류. 있다고 적힌 것만 넣는다(공공데이터 편의시설과 같은 원칙).
    public static Set<FacilityType> toFacilityTypes(Map<String, String> detail) {
        Set<FacilityType> types = EnumSet.noneOf(FacilityType.class);
        String elevator = detail.getOrDefault("elevator", "");
        String restroom = detail.getOrDefault("restroom", "");
        String parking = detail.getOrDefault("parking", "");
        String exit = detail.getOrDefault("exit", "");
        String route = detail.getOrDefault("route", "");
        String braille = detail.getOrDefault("braileblock", "");
        if (says(elevator, "있음") && !says(elevator, "없음")) {
            types.add(FacilityType.ELEVATOR);
        }
        if (says(restroom, "장애인") && says(restroom, "있음")) {
            types.add(FacilityType.ACCESSIBLE_TOILET);
        }
        if (says(parking, "장애인") && says(parking, "있음")) {
            types.add(FacilityType.PARKING);
        }
        if (says(exit, "턱이 없") || says(exit, "휠체어 접근 가능")) {
            types.add(FacilityType.NO_STEP);
        }
        if (says(route, "경사로") && (says(route, "있음") || says(route, "설치"))) {
            types.add(FacilityType.RAMP);
        }
        if (says(braille, "있음") && !says(braille, "없음")) {
            types.add(FacilityType.BRAILLE_BLOCK);
        }
        return types;
    }

    private static boolean says(String text, String word) {
        return text.replace(" ", "").contains(word.replace(" ", ""));
    }

    private static Double distance(Double latitude, Double longitude, Place place) {
        if (latitude == null || longitude == null || place.latitude() == null || place.longitude() == null) {
            return null;
        }
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(place.latitude());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(place.longitude() - longitude);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
    }

    private static String compact(String value) {
        // "단국대학교"와 "단국대"를 같게 본다
        return value == null ? "" : SEPARATORS.matcher(REGION.matcher(value).replaceAll("")).replaceAll("").replace("대학교", "대");
    }
}
