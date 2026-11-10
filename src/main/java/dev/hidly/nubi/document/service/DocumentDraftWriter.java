package dev.hidly.nubi.document.service;

import dev.hidly.nubi.document.data.enums.DocumentType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// 신청 정보로 서류 초안을 쓴다. 공고에 이미 있는 지원은 묻지 않고, 확인이 필요한 항목만 문의 문자·통화 대본에 담는다.
// AI 모델이 정해지면 이 초안과 같은 사실만 넘겨 문장을 다듬게 하고, AI가 실패하면 이 초안을 그대로 쓴다.
@Component
public class DocumentDraftWriter {

    public record Facts(String organizationName, String managerName, String programTitle, LocalDateTime startAt,
                        int participantCount, int companionCount,
                        List<String> confirmed, List<String> toAsk, List<String> careNotes) {
        int total() {
            return participantCount + companionCount;
        }
    }

    public Map<DocumentType, String> write(Facts facts) {
        Map<DocumentType, String> documents = new EnumMap<>(DocumentType.class);
        documents.put(DocumentType.FORM, form(facts));
        documents.put(DocumentType.INQUIRY, inquiry(facts));
        documents.put(DocumentType.CALL_SCRIPT, callScript(facts));
        return documents;
    }

    private String form(Facts facts) {
        List<String> needs = new ArrayList<>(facts.confirmed());
        needs.addAll(facts.toAsk());
        return String.join("\n",
                "프로그램 참가 신청서",
                "프로그램: " + facts.programTitle() + when(facts, " (", ")"),
                "신청 기관: " + facts.organizationName(),
                "담당자: " + (facts.managerName() == null ? "[직접 입력: 이름]" : facts.managerName()) + " [직접 입력: 연락처]",
                "참가 인원: 이용자 %d명, 동반 인력 %d명 (총 %d명)".formatted(facts.participantCount(), facts.companionCount(), facts.total()),
                "필요한 지원: " + (needs.isEmpty() ? "없음" : String.join(", ", needs)),
                "미리 여쭐 점: " + (facts.careNotes().isEmpty() ? "없음" : String.join(" / ", facts.careNotes())));
    }

    private String inquiry(Facts facts) {
        String opening = "안녕하세요, %s예요. %s%s에 이용자 %d명과 동반 %d명이 함께 가려고 해요."
                .formatted(facts.organizationName(), when(facts, "", " "), facts.programTitle(),
                        facts.participantCount(), facts.companionCount());
        if (facts.toAsk().isEmpty()) {
            return opening + " 신청 방법을 알려 주실 수 있을까요?";
        }
        return opening + " " + String.join(", ", facts.toAsk()) + "이(가) 가능한지 알려 주실 수 있을까요?";
    }

    private String callScript(Facts facts) {
        String opening = "\"%s%s에 %d명 신청하려고 해요."
                .formatted(when(facts, "", " "), facts.programTitle(), facts.total());
        if (facts.toAsk().isEmpty()) {
            return opening + " 신청은 어떻게 하면 될까요?\"";
        }
        return opening + " " + String.join(", ", facts.toAsk()) + "이(가) 가능한지 여쭤봐도 될까요?\"";
    }

    // "11월 15일" 처럼 날짜만 붙인다. 시작일이 없으면 빈 문자열.
    private static String when(Facts facts, String prefix, String suffix) {
        if (facts.startAt() == null) {
            return "";
        }
        return prefix + "%d월 %d일".formatted(facts.startAt().getMonthValue(), facts.startAt().getDayOfMonth()) + suffix;
    }
}
