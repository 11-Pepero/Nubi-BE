package dev.hidly.nubi.application.service;

// 알림 본문은 200자까지 저장된다(notification.body). 행사 제목이 길어도 넘지 않게 자른다.
final class NotificationText {
    private static final int BODY_LIMIT = 200;

    private NotificationText() {
    }

    static String body(String text) {
        return text.length() <= BODY_LIMIT ? text : text.substring(0, BODY_LIMIT - 1) + "…";
    }
}
