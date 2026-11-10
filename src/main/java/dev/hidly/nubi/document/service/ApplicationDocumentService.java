package dev.hidly.nubi.document.service;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.entity.ApplicationCare;
import dev.hidly.nubi.application.data.entity.ApplicationSupport;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.ApplicationAccessDeniedException;
import dev.hidly.nubi.application.data.exception.ApplicationNotFoundException;
import dev.hidly.nubi.application.data.exception.InvalidApplicationStatusException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.common.response.GlobalResponse;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.document.data.dto.response.ApplicationDocumentsResponseDto;
import dev.hidly.nubi.document.data.entity.ApplicationDocument;
import dev.hidly.nubi.document.data.enums.DocumentType;
import dev.hidly.nubi.document.data.repository.ApplicationDocumentRepository;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.organization.data.repository.MemberRepository;
import dev.hidly.nubi.program.data.entity.EligibilityReasons;
import dev.hidly.nubi.program.data.enums.ItemResult;
import dev.hidly.nubi.program.service.EligibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationDocumentService {
    private static final Set<ApplicationStatus> CLOSED = Set.of(ApplicationStatus.CANCELLED, ApplicationStatus.REJECTED);

    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository applicationDocumentRepository;
    private final MemberRepository memberRepository;
    private final EligibilityService eligibilityService;
    private final DocumentDraftWriter documentDraftWriter;

    // 신청서·문의 문자·통화 대본을 쓴다. 이미 있으면 다시 쓴다("다시 쓰기").
    @Transactional
    public GlobalResponse<ApplicationDocumentsResponseDto> write(Long applicationId) {
        Application application = applicationRepository.findWithProgramById(applicationId)
                .orElseThrow(ApplicationNotFoundException::new);
        if (!application.isAppliedBy(SecurityUtil.getOrganizationId())) {
            throw new ApplicationAccessDeniedException();
        }
        if (CLOSED.contains(application.getStatus())) {
            throw new InvalidApplicationStatusException();
        }

        DocumentDraftWriter.Facts facts = factsOf(application);
        Map<DocumentType, ApplicationDocument> existing = applicationDocumentRepository
                .findAllByApplicationIdOrderByIdAsc(applicationId).stream()
                .collect(Collectors.toMap(ApplicationDocument::getType, Function.identity()));
        List<ApplicationDocument> documents = new ArrayList<>();
        documentDraftWriter.write(facts).forEach((type, content) -> {
            ApplicationDocument document = existing.get(type);
            if (document == null) {
                document = applicationDocumentRepository.save(ApplicationDocument.builder()
                        .application(application).type(type).content(content).build());
            } else {
                document.rewrite(content);
            }
            documents.add(document);
        });
        applicationDocumentRepository.flush();
        log.info("[writeDocuments] 신청 서류 작성: applicationId={}, 확인 필요 {}개", applicationId, facts.toAsk().size());

        return GlobalResponse.ok(new ApplicationDocumentsResponseDto(applicationId, facts.confirmed(), facts.toAsk(),
                documents.stream().map(ApplicationDocumentsResponseDto.Document::from).toList()));
    }

    // 신청할 때 복사해 둔 지원 항목을 공고 조건과 견줘, 공고에 있는 것과 물어봐야 하는 것으로 나눈다.
    private DocumentDraftWriter.Facts factsOf(Application application) {
        Map<Long, ItemResult> results = application.getGroup() == null ? Map.of()
                : eligibilityService.judgeProgram(application.getProgram(), application.getGroup().getId())
                .reasons().items().stream()
                .collect(Collectors.toMap(EligibilityReasons.Item::supportTagId, EligibilityReasons.Item::result, (a, b) -> a));

        List<String> confirmed = new ArrayList<>();
        List<String> toAsk = new ArrayList<>();
        for (ApplicationSupport support : application.getSupports()) {
            String need = support.getQuantity() == null ? support.getSupportTag().getLabel()
                    : "%s %d명".formatted(support.getSupportTag().getLabel(), support.getQuantity());
            if (results.get(support.getSupportTag().getId()) == ItemResult.MET) {
                confirmed.add(need);
            } else {
                toAsk.add(need);
            }
        }
        List<String> careNotes = application.getCares().stream()
                .map(ApplicationCare::getContentSnapshot)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        String managerName = memberRepository.findById(SecurityUtil.getMemberId()).map(Member::getName).orElse(null);

        return new DocumentDraftWriter.Facts(application.getApplicantOrganization().getName(), managerName,
                application.getProgram().getTitle(), application.getProgram().getStartAt(),
                application.getParticipantCount(), application.getCompanionCount(), confirmed, toAsk, careNotes);
    }
}
