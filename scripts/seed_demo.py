#!/usr/bin/env python3
"""시연용 기관·행사·사진을 API로 넣는다.

    python3 scripts/seed_demo.py https://52-78-129-31.sslip.io
    python3 scripts/seed_demo.py http://localhost:8080
    python3 scripts/seed_demo.py https://52-78-129-31.sslip.io --replace-photos

- 시연용 기관 4곳을 가입시키고(이미 있으면 로그인) 기관마다 앞으로 열릴 행사를 등록한다.
- 장소는 카카오 장소 검색으로 실제 용인 장소를 고르고 등록한다(편의시설은 서버가 공공데이터로 채운다).
- 사진은 위키미디어 공용(Wikimedia Commons)에서 주제어로 찾은 무료 사진을 올린다.
- 사진 자리에 "File:파일 이름"을 쓰면 그 사진을 그대로 쓴다(검색 결과가 주제와 안 맞을 때 골라 둔 사진).
- 같은 제목의 행사가 이미 있으면 건너뛰므로 여러 번 실행해도 된다. 사진만 실패했던 행사는 사진을 채운다.
- --replace-photos: 이미 있는 행사 중 "File:"로 사진을 정해 둔 행사의 사진을 그 사진으로 바꾼다.
- 비밀번호는 SEED_PASSWORD 환경변수(기본 nubi1234!).
"""
import datetime as dt
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

PASSWORD = os.environ.get("SEED_PASSWORD", "nubi1234!")
USER_AGENT = "NubiSeed/1.0 (demo data loader)"

ORGS = [
    {"key": "nulbom", "orgName": "용인 늘봄 주간보호센터", "type": "DAY_CARE", "email": "demo-nulbom@nubi.kr",
     "address": "경기 용인시 처인구 중부대로 1199", "phone": "031-321-1001", "latitude": 37.2344, "longitude": 127.2017},
    {"key": "haeoreum", "orgName": "기흥 해오름 장애인복지관", "type": "WELFARE_CENTER", "email": "demo-haeoreum@nubi.kr",
     "address": "경기 용인시 기흥구 구갈로 60", "phone": "031-284-2002", "latitude": 37.2803, "longitude": 127.1150},
    {"key": "daon", "orgName": "수지 다온 보호작업장", "type": "WORKSHOP", "email": "demo-daon@nubi.kr",
     "address": "경기 용인시 수지구 포은대로 435", "phone": "031-263-3003", "latitude": 37.3222, "longitude": 127.0975},
    {"key": "sports", "orgName": "용인시 장애인체육회", "type": "PUBLIC", "email": "demo-sports@nubi.kr",
     "address": "경기 용인시 처인구 중부대로 1199", "phone": "031-324-4004", "latitude": 37.2340, "longitude": 127.2010},
]

# (기관, 제목, 분야, 장소 검색어, 사진 주제어, 며칠 뒤, 시작 시, 시작 분, 몇 시간, 정원, 마감(며칠 전), 지원 코드, 소개)
EVENTS = [
    ("haeoreum", "손끝으로 듣는 겨울 음악회", "CULTURE", "용인포은아트홀", "File:اجرای کوارتت زهی٬کنسرت مایستر کاوه کشاورزا.jpg",
     9, 15, 0, 2, 60, 3, ["WHEELCHAIR", "SIGN_LANGUAGE", "QUIET_REST_SPACE", "COMPANION"],
     "현악 4중주가 들려주는 겨울 노래를 가까이에서 들어요. 공연 중간에 악기를 직접 만져 볼 수 있는 시간이 있고, "
     "소리가 크게 느껴지면 언제든 옆 쉼터에서 쉬었다 와도 괜찮아요."),
    ("nulbom", "다 같이 그리는 큰 벽화", "CULTURE", "용인문화예술원", "community mural painting",
     12, 13, 0, 3, 20, 4, ["WHEELCHAIR", "EASY_WORDS", "PICTURE_BOARD", "FLEXIBLE_TIME"],
     "커다란 벽 한 면에 우리 동네 지도를 함께 그려요. 붓이 어려우면 손바닥 도장으로 참여해도 돼요. 완성된 벽화 앞에서 단체 사진도 찍어요."),
    ("haeoreum", "옛 물건 이야기 박물관 나들이", "CULTURE", "용인시박물관", "museum visitors looking at exhibits",
     15, 10, 30, 2, 20, 5, ["WHEELCHAIR", "WALKING_AID", "EASY_WORDS", "COMPANION"],
     "할머니 할아버지 때 쓰던 물건들을 보고, 해설 선생님께 쉬운 말로 이야기를 들어요. 마지막에는 옛날 놀이 체험도 있어요."),
    ("daon", "한국민속촌 겨울 풍경 산책", "CULTURE", "한국민속촌", "Korean Folk Village Yongin",
     20, 11, 0, 4, 30, 6, ["WHEELCHAIR", "VEHICLE_ASSIST", "COMPANION", "MEAL_ASSIST"],
     "초가집과 장터 골목을 천천히 걸으며 겨울 풍경을 구경해요. 점심은 장터에서 국밥을 같이 먹고, 길이 고른 길로만 다녀요."),
    ("nulbom", "소리 줄인 영화 상영회", "CULTURE", "CGV 용인", "movie theater audience",
     7, 14, 0, 2, 40, 2, ["WHEELCHAIR", "LOW_STIMULUS", "QUIET_REST_SPACE", "FLEXIBLE_TIME"],
     "불을 조금 켜 두고 소리를 줄여서 가족 애니메이션을 봐요. 영화 중간에 움직이거나 소리를 내도 괜찮은, 마음 편한 상영회예요."),
    ("daon", "크리스마스 캐럴 합창단", "CULTURE", "수지구청", "christmas choir singing",
     35, 16, 0, 2, 25, 10, ["WHEELCHAIR", "EASY_WORDS", "PICTURE_BOARD"],
     "네 번 모여 캐럴 세 곡을 연습하고, 크리스마스 전날 구청 로비에서 작은 공연을 해요. 노래를 몰라도 그림 악보로 따라 할 수 있어요."),

    ("sports", "휠체어 볼링 한판", "SPORTS", "용인 볼링장", "File:Bowling Alley 1955 (12766767553).jpg",
     10, 14, 0, 2, 24, 3, ["WHEELCHAIR", "POWER_WHEELCHAIR", "COMPANION", "ACCESSIBLE_TOILET"],
     "볼링공을 굴리는 경사대를 준비했어요. 팀을 나눠 두 게임을 하고, 가장 많이 웃은 팀에게 작은 메달을 줘요."),
    ("haeoreum", "기흥호수 한 바퀴 걷기", "SPORTS", "기흥호수공원", "lakeside walking path park",
     6, 10, 0, 2, 30, 2, ["WHEELCHAIR", "WALKING_AID", "NO_STAIRS", "COMPANION"],
     "평평한 호숫가 산책길 3km를 같이 걸어요. 중간 쉼터에서 따뜻한 차를 마시고, 힘들면 짧은 코스로 돌아와도 돼요."),
    ("sports", "보치아 친선 경기", "SPORTS", "용인실내체육관", "boccia paralympic",
     16, 13, 30, 3, 32, 5, ["WHEELCHAIR", "POWER_WHEELCHAIR", "ONE_TO_ONE", "ACCESSIBLE_TOILET"],
     "공을 던지거나 굴려서 표적 공에 가까이 붙이는 보치아를 해요. 처음 해 보는 사람도 규칙을 그림으로 쉽게 배울 수 있어요."),
    ("sports", "겨울 실내 수영 교실", "SPORTS", "용인시민체육공원", "File:Learning to swim (8801704443).jpg",
     13, 10, 0, 1, 12, 5, ["ONE_TO_ONE", "TOILET_ASSIST", "ACCESSIBLE_TOILET", "COMPANION"],
     "따뜻한 실내 수영장에서 물에 뜨는 것부터 천천히 배워요. 선생님 한 명이 한 사람씩 옆에서 함께해요."),
    ("daon", "탁구 왕 뽑기 대회", "SPORTS", "수지체육공원", "table tennis match",
     23, 14, 0, 3, 20, 7, ["WHEELCHAIR", "FLEXIBLE_TIME", "COMPANION"],
     "앉아서도 칠 수 있게 낮은 탁구대를 준비했어요. 조별 경기를 하고 결승전은 모두가 응원해요."),
    ("nulbom", "에버랜드 겨울 나들이", "SPORTS", "에버랜드", "Everland theme park",
     27, 10, 0, 6, 20, 10, ["WHEELCHAIR", "VEHICLE_ASSIST", "COMPANION", "MEAL_ASSIST", "QUIET_REST_SPACE"],
     "센터 차량으로 함께 출발해 동물원과 퍼레이드를 구경해요. 줄 서기가 힘든 사람을 위해 쉬는 곳을 미리 정해 두었어요."),

    ("haeoreum", "손뜨개 목도리 만들기", "EDUCATION", "기흥장애인복지관", "File:Pink knitting in front of pink sweatshirt.JPG",
     8, 10, 30, 2, 12, 3, ["EASY_WORDS", "ONE_TO_ONE", "FLEXIBLE_TIME"],
     "굵은 실과 큰 바늘로 나만의 목도리를 떠요. 실과 바늘은 모두 준비해 드리고, 처음이어도 천천히 알려 드려요."),
    ("daon", "우리 동네 빵집 체험", "EDUCATION", "용인시수지장애인복지관", "baking bread class",
     11, 14, 0, 2, 10, 4, ["EASY_WORDS", "PICTURE_BOARD", "SPECIAL_DIET"],
     "반죽을 만지고 모양을 빚어 소금빵을 구워요. 다 구운 빵은 예쁘게 포장해서 집에 가져가요."),
    ("nulbom", "휴대폰으로 사진 잘 찍기", "EDUCATION", "처인장애인복지관", "smartphone photography",
     17, 14, 0, 2, 15, 5, ["EASY_WORDS", "PICTURE_BOARD", "COMPANION"],
     "휴대폰 카메라로 밝고 예쁘게 찍는 방법을 배우고, 복지관 정원에서 직접 찍어 봐요. 가장 멋진 사진은 게시판에 걸어요."),
    ("haeoreum", "용돈 기입장 쉬운 경제 교실", "EDUCATION", "기흥구청", "File:10000 won banknote.jpg",
     22, 10, 0, 2, 15, 6, ["EASY_WORDS", "PICTURE_BOARD", "FLEXIBLE_TIME"],
     "물건 값을 비교하고 용돈 기입장을 쓰는 법을 놀이로 배워요. 가짜 돈으로 장보기 게임도 해요."),
    ("daon", "흙으로 만드는 도자기 컵", "EDUCATION", "용인 도예공방", "pottery wheel class",
     29, 13, 0, 3, 10, 8, ["EASY_WORDS", "ONE_TO_ONE", "LOW_STIMULUS"],
     "말랑한 흙을 손으로 빚어 나만의 컵을 만들어요. 구운 컵은 2주 뒤에 센터로 보내 드려요."),
    ("nulbom", "텃밭에서 배우는 겨울 채소", "EDUCATION", "용인농촌테마파크", "vegetable garden farm",
     33, 10, 30, 2, 20, 8, ["WHEELCHAIR", "VEHICLE_ASSIST", "COMPANION"],
     "비닐하우스 텃밭에서 시금치와 배추를 직접 뽑아 보고, 수확한 채소로 비빔밥을 만들어 먹어요."),

    ("haeoreum", "아침 스트레칭 요가", "HEALTH", "수지구보건소", "yoga class stretching",
     5, 10, 0, 1, 20, 1, ["WHEELCHAIR", "EASY_WORDS", "FLEXIBLE_TIME"],
     "의자에 앉아서도 할 수 있는 쉬운 요가로 굳은 몸을 풀어요. 매트와 물은 준비되어 있어요."),
    ("daon", "마음 쉼표 명상 시간", "HEALTH", "기흥구보건소", "File:Meditation at the bank of river.jpg",
     14, 15, 0, 1, 15, 4, ["QUIET_REST_SPACE", "LOW_STIMULUS", "EASY_WORDS"],
     "조용한 방에서 숨 쉬기와 몸 느끼기로 마음을 편하게 해요. 불안할 때 쓸 수 있는 방법을 하나씩 가져가요."),
    ("nulbom", "건강 밥상 요리 교실", "HEALTH", "처인구보건소", "File:Making kimbap by johl.jpg",
     19, 11, 0, 2, 12, 5, ["EASY_WORDS", "PICTURE_BOARD", "SPECIAL_DIET", "MEAL_ASSIST"],
     "영양사 선생님과 채소 듬뿍 김밥을 만들어요. 알레르기가 있으면 신청할 때 알려 주세요."),
    ("haeoreum", "웃는 이 치과 검진 데이", "HEALTH", "용인시처인구보건소", "File:US Navy 101006-N-6770T-176 Cmdr. Bradley J. Smith, a dentist, examines a patient before extracting a tooth at San Miguel National High School.jpg",
     25, 13, 0, 3, 30, 7, ["WHEELCHAIR", "LOW_STIMULUS", "COMPANION", "ACCESSIBLE_TOILET"],
     "치과 선생님이 이를 살펴보고 바르게 이 닦는 법을 알려 줘요. 기다리는 동안 쉴 수 있는 조용한 자리를 마련했어요."),

    ("sports", "겨울 방한용품 나눔 장터", "BENEFIT", "용인시청", "flea market stalls",
     18, 11, 0, 4, 100, 3, ["WHEELCHAIR", "ACCESSIBLE_TOILET", "COMPANION"],
     "목도리, 장갑, 담요 같은 방한용품을 한 사람에 세 가지씩 골라 가요. 따뜻한 어묵탕도 나눠 드려요."),
    ("daon", "활동 지원 상담 데이", "BENEFIT", "수지구청", "counseling consultation",
     21, 13, 0, 4, 40, 5, ["WHEELCHAIR", "SIGN_LANGUAGE", "EASY_WORDS"],
     "장애인 활동 지원과 복지 혜택을 담당 선생님과 1:1로 상담해요. 필요한 서류를 쉬운 말로 정리한 안내지를 드려요."),
]


def call(base, method, path, token=None, body=None, raw=None, content_type=None):
    url = base.rstrip("/") + "/api/v1" + path
    data = None
    headers = {"User-Agent": USER_AGENT}
    if token:
        headers["Authorization"] = "Bearer " + token
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    elif raw is not None:
        data = raw
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode())
        except Exception:
            return {"success": False, "error": {"code": str(e.code), "message": e.reason}}


def login(base, org):
    signup = {k: org[k] for k in ("orgName", "type", "address", "email", "phone", "latitude", "longitude")}
    signup["password"] = PASSWORD
    result = call(base, "POST", "/auth/signup", body=signup)
    if not result.get("success"):
        result = call(base, "POST", "/auth/login", body={"email": org["email"], "password": PASSWORD})
    if not result.get("success"):
        raise SystemExit(f"{org['email']} 로그인 실패: {result.get('error')}")
    return result["data"]["accessToken"]


def support_tag_ids(base, token):
    result = call(base, "GET", "/support-tags", token)
    return {tag["code"]: tag["id"] for group in result.get("data", []) for tag in group.get("tags", [])}


def venue_id(base, token, query, cache):
    if query in cache:
        return cache[query]
    places = call(base, "GET", "/venues/places?" + urllib.parse.urlencode({"query": query}), token).get("data") or []
    if not places:
        print(f"  ! 장소를 찾지 못함: {query}")
        cache[query] = None
        return None
    place = places[0]
    if place.get("venueId"):
        cache[query] = place["venueId"]
    else:
        registered = call(base, "POST", "/venues", token, body={
            "name": place["name"],
            "address": place.get("roadAddress") or place.get("address") or place["name"],
            "latitude": place["latitude"], "longitude": place["longitude"]})
        cache[query] = (registered.get("data") or {}).get("venueId")
    print(f"  장소 {query} → {place['name']} (#{cache[query]})")
    return cache[query]


def fetch(url, attempts=5):
    """위키미디어는 짧은 시간에 많이 부르면 429를 준다. 기다렸다가 다시 부른다."""
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": USER_AGENT}), timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == attempts - 1:
                raise
            wait = int(e.headers.get("Retry-After") or 0) or 10 * (attempt + 1)
            time.sleep(min(wait, 60))


def find_photo(keyword):
    """위키미디어 공용에서 주제어로 가로 사진(JPEG) 하나를 찾아 1200px로 받는다. "File:"이면 그 사진을 받는다."""
    if keyword.startswith("File:"):
        params = urllib.parse.urlencode({"action": "query", "titles": keyword, "prop": "imageinfo",
                                         "iiprop": "url", "iiurlwidth": 1200, "format": "json"})
        try:
            pages = json.loads(fetch("https://commons.wikimedia.org/w/api.php?" + params))["query"]["pages"]
            return fetch(next(iter(pages.values()))["imageinfo"][0]["thumburl"])
        except Exception as e:
            print(f"  ! 사진을 받지 못함: {keyword} ({e})")
            return None
    params = urllib.parse.urlencode({
        "action": "query", "generator": "search", "gsrsearch": f"filetype:bitmap {keyword}",
        "gsrnamespace": 6, "gsrlimit": 15, "prop": "imageinfo", "iiprop": "url|size|mime",
        "iiurlwidth": 1200, "format": "json"})
    try:
        pages = json.loads(fetch("https://commons.wikimedia.org/w/api.php?" + params)).get("query", {}).get("pages", {})
    except Exception as e:
        print(f"  ! 사진 검색 실패: {keyword} ({e})")
        return None
    for page in sorted(pages.values(), key=lambda p: p.get("index", 99)):
        info = (page.get("imageinfo") or [{}])[0]
        if info.get("mime") != "image/jpeg" or info.get("width", 0) < 900 or info.get("width", 0) < info.get("height", 0):
            continue
        try:
            body = fetch(info["thumburl"])
            if len(body) < 4_500_000:
                return body
        except Exception:
            continue
    print(f"  ! 알맞은 사진이 없음: {keyword}")
    return None


def upload_photo(base, token, event_id, photo):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"images\"; filename=\"cover.jpg\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + photo + f"\r\n--{boundary}--\r\n".encode()
    return call(base, "POST", f"/events/{event_id}/images", token, raw=body,
                content_type=f"multipart/form-data; boundary={boundary}").get("success")


def replace_photo(base, token, event_id, photo):
    images = (call(base, "GET", f"/programs/{event_id}", token).get("data") or {}).get("images") or []
    for image in images:
        call(base, "DELETE", f"/events/{event_id}/images/{image['imageId']}", token)
    return upload_photo(base, token, event_id, photo)


def main():
    args = [arg for arg in sys.argv[1:] if not arg.startswith("--")]
    replace_photos = "--replace-photos" in sys.argv
    if len(args) != 1:
        raise SystemExit(__doc__)
    base = args[0]
    tokens = {org["key"]: login(base, org) for org in ORGS}
    print(f"기관 {len(tokens)}곳 로그인")
    tags = support_tag_ids(base, next(iter(tokens.values())))
    venues = {}
    today = dt.date.today()
    created = skipped = photos = 0

    for (org, title, category, venue_query, photo_keyword, days, hour, minute, hours, capacity, deadline_before,
         supports, description) in EVENTS:
        token = tokens[org]
        existing = call(base, "GET", "/events?size=100", token).get("data", {}).get("content", [])
        same = next((event for event in existing if event["title"] == title), None)
        if same:
            skipped += 1
            if replace_photos and photo_keyword.startswith("File:"):
                photo = find_photo(photo_keyword)
                if photo and replace_photo(base, token, same["eventId"], photo):
                    photos += 1
                    print(f"  사진 바꿈 #{same['eventId']} {title}")
                time.sleep(2)
            # 전에 사진 올리기만 실패했으면 사진만 채운다
            elif not same.get("thumbnailUrl"):
                photo = find_photo(photo_keyword)
                if photo and upload_photo(base, token, same["eventId"], photo):
                    photos += 1
                    print(f"  사진 채움 #{same['eventId']} {title}")
                time.sleep(2)
            continue
        start = dt.datetime.combine(today + dt.timedelta(days=days), dt.time(hour, minute))
        event = {
            "title": title, "category": category, "description": description,
            "venueId": venue_id(base, token, venue_query, venues),
            "startAt": start.isoformat(), "endAt": (start + dt.timedelta(hours=hours)).isoformat(),
            "deadlineAt": (start - dt.timedelta(days=deadline_before)).replace(hour=18, minute=0).isoformat(),
            "capacity": capacity, "contactPhone": next(o["phone"] for o in ORGS if o["key"] == org),
            "status": "OPEN",
            "conditions": [{"supportTagId": tags[code], "status": "PROVIDED"} for code in supports if code in tags],
        }
        result = call(base, "POST", "/events", token, body=event)
        if not result.get("success"):
            print(f"  ! 행사 등록 실패: {title} ({result.get('error')})")
            continue
        created += 1
        event_id = result["data"]["programId"]
        photo = find_photo(photo_keyword)
        if photo and upload_photo(base, token, event_id, photo):
            photos += 1
        print(f"  행사 #{event_id} {title} ({start:%m/%d %H:%M})")
        time.sleep(2)

    print(f"끝: 새 행사 {created}개, 이미 있어 건너뜀 {skipped}개, 사진 {photos}장")
    print(f"시연 계정: {', '.join(o['email'] for o in ORGS)} / 비밀번호 SEED_PASSWORD")


if __name__ == "__main__":
    main()
