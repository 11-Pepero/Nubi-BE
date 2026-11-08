package dev.hidly.nubi.application.data.entity;

import dev.hidly.nubi.application.data.enums.ApplicationStatus;
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
