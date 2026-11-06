package dev.hidly.nubi.organization.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.enums.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role;

    @Column(length = 20)
    private String phone;

    @Builder
    private Member(Organization organization, String email, String passwordHash, String name, MemberRole role, String phone) {
        this.organization = organization;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.phone = phone;
    }
}
