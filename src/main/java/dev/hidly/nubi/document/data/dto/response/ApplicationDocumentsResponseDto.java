package dev.hidly.nubi.document.data.dto.response;

import dev.hidly.nubi.document.data.entity.ApplicationDocument;
import dev.hidly.nubi.document.data.enums.DocumentType;

import java.time.LocalDateTime;
import java.util.List;

// 신청 준비(화면 08): 함께 가는 사람 칩(공고에 있어요 / 물어봐야 해요)과 서류 세 장
public record ApplicationDocumentsResponseDto(
        Long applicationId,
        List<String> confirmed,
        List<String> toAsk,
        // true면 AI가 다듬은 서류, false면 AI 없이 쓴 초안(키가 없거나 AI 호출이 실패한 경우)
        boolean aiWritten,
        List<Document> documents
) {
    public record Document(Long documentId, DocumentType type, String content, LocalDateTime updatedAt) {
        public static Document from(ApplicationDocument document) {
            return new Document(document.getId(), document.getType(), document.getContent(), document.getUpdatedAt());
        }
    }
}
