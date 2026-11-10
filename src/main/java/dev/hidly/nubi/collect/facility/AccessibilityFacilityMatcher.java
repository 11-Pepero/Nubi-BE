package dev.hidly.nubi.collect.facility;

import dev.hidly.nubi.collect.facility.AccessibilityFacilityClient.Place;
import dev.hidly.nubi.program.data.enums.FacilityType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 우리 장소와 장애인편의시설 목록의 시설을 주소로 잇고, 설치된 편의시설 이름을 FacilityType으로 바꾼다.
public final class AccessibilityFacilityMatcher {
    private static final Pattern ROAD = Pattern.compile("([가-힣0-9]+(?:로|길))\\s*(\\d+(?:-\\d+)?)");
    private static final Pattern LOT = Pattern.compile("([가-힣0-9]+(?:동|리|가))\\s*(산?\\s*\\d+(?:-\\d+)?)");

    // 사람이 사는 집은 행사 장소가 아니다. 같은 주소에 아파트가 같이 있으면 잘못 이어질 수 있어 뺀다.
    private static final List<String> HOUSING = List.of("아파트", "연립주택", "다세대주택", "단독주택", "다가구주택", "기숙사", "공동주택");

    // 한 건물에 여러 가게·사무실이 들어가는 종류. 주소만 같다고 우리 장소라고 볼 수 없어 이름도 겹쳐야 잇는다.
    private static final List<String> SHARED_BUILDING = List.of("일반업무시설", "소매점", "음식점", "제과점", "공장", "숙박", "주차장", "대피소",
            "도매시정", "소매시장", "대형마트");

    private AccessibilityFacilityMatcher() {
    }

    // "경기도 용인시 수지구 포은대로 499" → "포은대로499", 도로명이 없으면 "남동555"
    public static Optional<String> addressKey(String address) {
        if (address == null) {
            return Optional.empty();
        }
        String cleaned = address.replaceAll("\\(.*?\\)", " ");
        Matcher road = ROAD.matcher(cleaned);
        if (road.find()) {
            return Optional.of(road.group(1) + road.group(2));
        }
        Matcher lot = LOT.matcher(cleaned);
        if (lot.find()) {
            return Optional.of(lot.group(1) + lot.group(2).replace(" ", ""));
        }
        return Optional.empty();
    }

    public static Map<String, List<Place>> indexByAddress(Collection<Place> places) {
        Map<String, List<Place>> index = new HashMap<>();
        for (Place place : places) {
            if (place.type() != null && HOUSING.stream().anyMatch(place.type()::contains)) {
                continue;
            }
            addressKey(place.address()).ifPresent(key -> index.computeIfAbsent(key, k -> new ArrayList<>()).add(place));
        }
        return index;
    }

    // 같은 주소의 시설이 하나면 그것, 여럿이면 이름이 겹치는 것. 고를 수 없으면 비운다.
    public static Optional<Place> match(String venueName, String venueAddress, Map<String, List<Place>> index) {
        Optional<String> key = addressKey(venueAddress);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        List<Place> candidates = index.getOrDefault(key.get(), List.of());
        Optional<Place> byName = candidates.stream().filter(place -> namesOverlap(venueName, place.name())).findFirst();
        if (byName.isPresent()) {
            return byName;
        }
        if (candidates.size() == 1 && !isSharedBuilding(candidates.getFirst())) {
            return Optional.of(candidates.getFirst());
        }
        return Optional.empty();
    }

    private static boolean namesOverlap(String venueName, String placeName) {
        String compactVenue = compact(venueName);
        String compactPlace = compact(placeName);
        return compactVenue.length() >= 2 && compactPlace.length() >= 2
                && (compactVenue.contains(compactPlace) || compactPlace.contains(compactVenue));
    }

    private static boolean isSharedBuilding(Place place) {
        return place.type() != null && SHARED_BUILDING.stream().anyMatch(place.type()::contains);
    }

    // 장애인편의시설 평가 항목 이름 → 우리 편의시설 종류
    public static Set<FacilityType> toFacilityTypes(Collection<String> installedItems) {
        Set<FacilityType> types = EnumSet.noneOf(FacilityType.class);
        for (String item : installedItems) {
            String name = item.replace(" ", "");
            if (name.contains("승강기") || name.contains("엘리베이터")) {
                types.add(FacilityType.ELEVATOR);
            }
            if (name.contains("화장실")) {
                types.add(FacilityType.ACCESSIBLE_TOILET);
            }
            if (name.contains("주차")) {
                types.add(FacilityType.PARKING);
            }
            if (name.contains("높이차이제거") || name.contains("단차")) {
                types.add(FacilityType.NO_STEP);
            }
            if (name.contains("경사로")) {
                types.add(FacilityType.RAMP);
            }
            if (name.contains("점자블록") || name.contains("유도및안내설비")) {
                types.add(FacilityType.BRAILLE_BLOCK);
            }
        }
        return types;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replaceAll("[\\s()·,]", "");
    }
}
