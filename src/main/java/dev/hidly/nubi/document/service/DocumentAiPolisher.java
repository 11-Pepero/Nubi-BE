package dev.hidly.nubi.document.service;

import dev.hidly.nubi.common.ai.OpenAiClient;
import dev.hidly.nubi.document.data.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// 초안과 같은 사실만 AI에 넘겨 문장을 다듬는다. 새 사실을 만들지 못하게 하고,
// 문의 문자·통화 대본은 "물어봐야 해요" 항목만 묻게 한다. 빠지거나 이상한 서류는 초안을 그대로 쓴다.
@Component
@RequiredArgsConstructor
public class DocumentAiPolisher {
    private static final Map<DocumentType, String> FIELDS = Map.of(
            DocumentType.FORM, "form",
            DocumentType.INQUIRY, "inquiry",
            DocumentType.CALL_SCRIPT, "callScript");
    private static final Map<DocumentType, Integer> MAX_LENGTH = Map.of(
            DocumentType.FORM, 2000,
            DocumentType.INQUIRY, 600,
            DocumentType.CALL_SCRIPT, 400);

    static final String SYSTEM_PROMPT = """
            너는 용인시 장애인복지시설 담당 사회복지사가 외부 프로그램에 단체로 신청할 때 서류를 다듬어 주는 도우미다.
            입력으로 facts(사실)와 drafts(초안)가 JSON으로 온다. 규칙:
            1. facts에 없는 사실(날짜, 시간, 연락처, 인원, 지원 항목, 기관 이름)을 새로 만들지 않는다. 숫자는 facts 그대로 쓴다.
            2. 문의 문자(inquiry)와 통화 대본(callScript)은 facts.toAsk 항목만 묻는다. facts.confirmed 항목은 공고에 이미 있으니 묻지 않는다.
               toAsk가 비어 있으면 신청 방법만 묻는다.
            3. 이용자의 실명이나 진단명을 쓰지 않는다. 개별 케어는 facts.careNotes 내용만 쓴다.
            4. 신청서(form)는 첫 줄 "프로그램 참가 신청서"와 "라벨: 값" 줄 구성을 그대로 두고, 값만 자연스럽게 다듬는다.
               "[직접 입력: ...]" 자리는 그대로 둔다. "미리 여쭐 점"은 careNotes를 정중한 문장으로 바꾼다.
            5. 문의 문자는 정중한 존댓말로 300자 이내, 통화 대본은 큰따옴표로 감싼 2~3문장으로 쓴다.
               둘 다 날짜(있으면)와 인원(이용자, 동반)을 꼭 넣는다. 받는 사람이 무엇을 준비할지 알 수 있어야 한다.
            6. 응답은 JSON 객체 하나로만 한다: {"form": "...", "inquiry": "...", "callScript": "..."}
            """;

    public record Result(Map<DocumentType, String> documents, boolean aiWritten) {
    }

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public Result polish(DocumentDraftWriter.Facts facts, Map<DocumentType, String> drafts) {
        if (!openAiClient.isEnabled()) {
            return new Result(drafts, false);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("facts", facts);
        Map<String, String> draftFields = new LinkedHashMap<>();
        drafts.forEach((type, content) -> draftFields.put(FIELDS.get(type), content));
        input.put("drafts", draftFields);

        Optional<JsonNode> response = openAiClient.completeJson(SYSTEM_PROMPT, objectMapper.writeValueAsString(input));
        if (response.isEmpty()) {
            return new Result(drafts, false);
        }
        Map<DocumentType, String> documents = new EnumMap<>(DocumentType.class);
        boolean anyAi = false;
        for (Map.Entry<DocumentType, String> draft : drafts.entrySet()) {
            String written = response.get().path(FIELDS.get(draft.getKey())).asString("").trim();
            if (isUsable(draft.getKey(), written)) {
                documents.put(draft.getKey(), written);
                anyAi = true;
            } else {
                documents.put(draft.getKey(), draft.getValue());
            }
        }
        return new Result(documents, anyAi);
    }

    private static boolean isUsable(DocumentType type, String written) {
        if (written.isBlank() || written.length() > MAX_LENGTH.get(type)) {
            return false;
        }
        return type != DocumentType.FORM || written.startsWith(DocumentPdfRenderer.titleOf(DocumentType.FORM));
    }
}
