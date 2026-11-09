package dev.hidly.nubi.application.service;

import dev.hidly.nubi.application.data.entity.Application;
import dev.hidly.nubi.application.data.entity.ApplicationCare;
import dev.hidly.nubi.application.data.entity.ApplicationSupport;
import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.InvalidApplicationRequestException;
import dev.hidly.nubi.application.data.repository.ApplicationRepository;
import dev.hidly.nubi.common.security.SecurityUtil;
import dev.hidly.nubi.organization.data.entity.Member;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.exception.ProgramAccessDeniedException;
import dev.hidly.nubi.program.data.exception.ProgramNotFoundException;
import dev.hidly.nubi.program.data.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 신청 명단 내려받기 (API-49, 화면 12 "명단 내려받기"). 주최 기관만, xlsx만.
// 이용자 실명은 저장하지 않으므로 개별 케어는 기관이 정한 별칭으로만 나간다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationExportService {
    public static final String XLSX = "xlsx";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] APPLICATION_HEADERS = {"신청 번호", "신청 기관", "그룹", "담당자", "연락처", "이용자", "동반 인력",
            "오는 방법", "필요한 지원", "개별 케어", "상태", "신청 시각"};
    private static final String[] CARE_HEADERS = {"신청 번호", "신청 기관", "별칭", "케어 내용"};

    private final ApplicationRepository applicationRepository;
    private final ProgramRepository programRepository;

    public record ExportFile(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public ExportFile export(Long eventId, String format, List<ApplicationStatus> statuses) {
        if (format != null && !XLSX.equalsIgnoreCase(format)) {
            throw new InvalidApplicationRequestException();
        }
        Program event = getHostedEvent(eventId);
        List<Application> applications = statuses == null || statuses.isEmpty()
                ? applicationRepository.findByProgramIdAndStatusNotInOrderByIdAsc(eventId, ApplicationStatus.RELEASED)
                : applicationRepository.findByProgramIdAndStatusInOrderByIdAsc(eventId, statuses);
        Map<Long, Member> contacts = firstMembers(applications.stream()
                .map(application -> application.getApplicantOrganization().getId())
                .collect(Collectors.toSet()));

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = headerStyle(workbook);
            CellStyle wrap = workbook.createCellStyle();
            wrap.setWrapText(true);

            Sheet sheet = workbook.createSheet("신청 기관");
            writeRow(sheet, 0, header, (Object[]) APPLICATION_HEADERS);
            int rowIndex = 1;
            for (Application application : applications) {
                Member contact = contacts.get(application.getApplicantOrganization().getId());
                Row row = writeRow(sheet, rowIndex++, null,
                        application.getId(),
                        application.getApplicantOrganization().getName(),
                        application.getGroup() == null ? "" : application.getGroup().getName(),
                        contact == null || contact.getName() == null ? "" : contact.getName(),
                        phoneOf(application, contact),
                        application.getParticipantCount(),
                        application.getCompanionCount(),
                        nullToEmpty(application.getTransport()),
                        supportsOf(application),
                        application.getCares().size(),
                        label(application.getStatus()),
                        application.getSubmittedAt() == null ? "" : application.getSubmittedAt().format(DATE_TIME));
                row.getCell(8).setCellStyle(wrap);
            }
            fitColumns(sheet, APPLICATION_HEADERS.length);

            Sheet cares = workbook.createSheet("개별 케어");
            writeRow(cares, 0, header, (Object[]) CARE_HEADERS);
            int careIndex = 1;
            for (Application application : applications) {
                for (ApplicationCare care : application.getCares()) {
                    Row row = writeRow(cares, careIndex++, null, application.getId(),
                            application.getApplicantOrganization().getName(), care.getAliasSnapshot(),
                            nullToEmpty(care.getContentSnapshot()));
                    row.getCell(3).setCellStyle(wrap);
                }
            }
            fitColumns(cares, CARE_HEADERS.length);

            workbook.write(out);
            log.info("[exportApplications] 명단 내려받기: eventId={}, 신청 {}건", eventId, applications.size());
            return new ExportFile(fileNameOf(event), out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("신청 명단 엑셀을 만들지 못했습니다.", e);
        }
    }

    private Program getHostedEvent(Long eventId) {
        Program event = programRepository.findById(eventId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(ProgramNotFoundException::new);
        if (!event.isHostedBy(SecurityUtil.getOrganizationId())) {
            throw new ProgramAccessDeniedException();
        }
        return event;
    }

    private Map<Long, Member> firstMembers(Set<Long> organizationIds) {
        Map<Long, Member> first = new LinkedHashMap<>();
        if (organizationIds.isEmpty()) {
            return first;
        }
        applicationRepository.findMembersOfOrganizations(organizationIds)
                .forEach(member -> first.putIfAbsent(member.getOrganization().getId(), member));
        return first;
    }

    private static String phoneOf(Application application, Member contact) {
        if (contact != null && contact.getPhone() != null) {
            return contact.getPhone();
        }
        return nullToEmpty(application.getApplicantOrganization().getPhone());
    }

    // "휠체어 2, 쉬운 말 안내" (수량이 비어 있으면 이용자 수 전체라 숫자를 붙이지 않는다)
    private static String supportsOf(Application application) {
        List<String> parts = new ArrayList<>();
        for (ApplicationSupport support : application.getSupports()) {
            String label = support.getSupportTag().getLabel();
            parts.add(support.getQuantity() == null ? label : label + " " + support.getQuantity());
        }
        return String.join(", ", parts);
    }

    // 엑셀은 화면처럼 한글 라벨로 보여 준다
    static String label(ApplicationStatus status) {
        return switch (status) {
            case PREPARING -> "준비 중";
            case SUBMITTED -> "새 신청";
            case INQUIRY -> "문의 있음";
            case WAITING -> "대기";
            case CONFIRMED -> "확정";
            case REJECTED -> "거절";
            case ATTENDED -> "다녀옴";
            case CANCELLED -> "취소";
        };
    }

    private static String fileNameOf(Program event) {
        String safeTitle = event.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safeTitle + "_신청명단.xlsx";
    }

    private static Row writeRow(Sheet sheet, int index, CellStyle style, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            Object value = values[i];
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : value.toString());
            }
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
        return row;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }

    // autoSizeColumn은 글꼴이 없는 서버에서 느리거나 실패할 수 있어 고정 폭을 쓴다.
    private static void fitColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
