package dev.hidly.nubi.application.data.entity;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
import dev.hidly.nubi.application.data.exception.InvalidApplicationStatusException;
import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import dev.hidly.nubi.program.data.entity.Program;
import dev.hidly.nubi.program.data.enums.ApplyMethod;
import dev.hidly.nubi.support.data.entity.SupportGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 신청은 기록이라 프로그램·기관·그룹이 지워져도 지우지 않는다.
@Getter
@Entity
@Table(name = "application")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_org_id", nullable = false)
    private Organization applicantOrganization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private SupportGroup group;

    @Column(nullable = false)
    private int participantCount;

    @Column(nullable = false)
    private int companionCount;

    @Column(length = 50)
    private String transport;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ApplyMethod channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ApplicationStatus status;

    private LocalDateTime submittedAt;

    private LocalDateTime decidedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ApplicationSupport> supports = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ApplicationCare> cares = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ApplicationMessage> messages = new ArrayList<>();

    @Builder
    private Application(Program program, Organization applicantOrganization, SupportGroup group,
                        int participantCount, int companionCount, String transport, ApplyMethod channel,
                        ApplicationStatus status, LocalDateTime submittedAt) {
        this.program = program;
        this.applicantOrganization = applicantOrganization;
        this.group = group;
        this.participantCount = participantCount;
        this.companionCount = companionCount;
        this.transport = transport;
        this.channel = channel;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    // 앱 안에서 바로 신청: 접수 기다림 상태로 시작
    public static Application direct(Program program, Organization applicant, SupportGroup group,
                                     int participantCount, int companionCount, String transport) {
        return Application.builder()
                .program(program)
                .applicantOrganization(applicant)
                .group(group)
                .participantCount(participantCount)
                .companionCount(companionCount)
                .transport(transport)
                .channel(ApplyMethod.DIRECT)
                .status(ApplicationStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();
    }

    // 외부 공고 신청 준비: 접수 전이라 submittedAt은 비워 두고, 채널은 공고의 접수 방법을 따른다.
    public static Application prepare(Program program, Organization applicant, SupportGroup group,
                                      int participantCount, int companionCount) {
        return Application.builder()
                .program(program)
                .applicantOrganization(applicant)
                .group(group)
                .participantCount(participantCount)
                .companionCount(companionCount)
                .channel(program.getApplyMethod())
                .status(ApplicationStatus.PREPARING)
                .build();
    }

    // 담당자가 외부에서 직접 접수를 끝냈다고 표시 (PREPARING → SUBMITTED)
    public void markSubmitted(ApplyMethod channel, LocalDateTime submittedAt) {
        if (status != ApplicationStatus.PREPARING) {
            throw new InvalidApplicationStatusException();
        }
        if (channel != null) {
            this.channel = channel;
        }
        this.status = ApplicationStatus.SUBMITTED;
        this.submittedAt = submittedAt;
    }

    // 주최 기관의 확정·대기·거절. 확정·거절이면 결정 시각을 남기고, 대기로 돌리면 지운다.
    public void decideByHost(ApplicationStatus next) {
        if (!status.canBeDecidedByHostAs(next)) {
            throw new InvalidApplicationStatusException();
        }
        this.status = next;
        this.decidedAt = next == ApplicationStatus.WAITING ? null : LocalDateTime.now();
    }

    // 신청 기관의 취소: 거절·취소·다녀옴이 아니면 참여 전까지 가능
    public void cancelByApplicant(LocalDateTime now) {
        boolean finished = status == ApplicationStatus.REJECTED || status == ApplicationStatus.CANCELLED
                || status == ApplicationStatus.ATTENDED;
        boolean started = program.getStartAt() != null && now.isAfter(program.getStartAt());
        if (finished || started) {
            throw new InvalidApplicationStatusException();
        }
        this.status = ApplicationStatus.CANCELLED;
    }

    // 신청 기관이 문의를 보내면 접수 기다림 신청은 "문의 있음"으로 표시한다. 다른 상태는 그대로 둔다.
    public void markInquiry() {
        if (status == ApplicationStatus.SUBMITTED) {
            this.status = ApplicationStatus.INQUIRY;
        }
    }

    // 취소된 신청에는 문의·답장을 더 남기지 않는다.
    public boolean acceptsMessages() {
        return status != ApplicationStatus.CANCELLED;
    }

    public boolean isAppliedBy(Long organizationId) {
        return applicantOrganization.getId().equals(organizationId);
    }

    public boolean belongsTo(Long programId) {
        return program.getId().equals(programId);
    }

    public void addSupport(ApplicationSupport support) {
        support.assignApplication(this);
        supports.add(support);
    }

    public void addCare(ApplicationCare care) {
        care.assignApplication(this);
        cares.add(care);
    }

    public void addMessage(ApplicationMessage message) {
        message.assignApplication(this);
        messages.add(message);
    }
}
