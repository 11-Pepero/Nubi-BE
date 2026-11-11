package dev.hidly.nubi.program.data.dto.response;

import dev.hidly.nubi.collect.kakao.KakaoLocalClient.PlaceCandidate;

// 장소 고르기(행사 등록)에서 보여 주는 카카오 장소 한 곳.
// venueId는 이미 우리 장소로 등록된 곳이면 그 번호, 아니면 null (고르면 장소 등록 API로 등록한다)
public record PlaceCandidateResponseDto(
        String kakaoPlaceId,
        String name,
        String category,
        String roadAddress,
        String address,
        String phone,
        double latitude,
        double longitude,
        Long venueId
) {
    public static PlaceCandidateResponseDto of(PlaceCandidate place, Long venueId) {
        return new PlaceCandidateResponseDto(place.id(), place.name(), place.category(), place.roadAddress(),
                place.address(), place.phone(), place.latitude(), place.longitude(), venueId);
    }
}
