package dev.hidly.nubi.support.data.entity;

import dev.hidly.nubi.common.jpa.BaseTimeEntity;
import dev.hidly.nubi.organization.data.entity.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "support_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportGroup extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int memberCount;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GroupSupport> supports = new ArrayList<>();

    @Builder
    private SupportGroup(Organization organization, String name, int memberCount) {
        this.organization = organization;
        this.name = name;
        this.memberCount = memberCount;
    }

    public void update(String name, int memberCount) {
        this.name = name;
        this.memberCount = memberCount;
    }

    public void replaceSupports(List<GroupSupport> newSupports) {
        supports.clear();
        newSupports.forEach(this::addSupport);
    }

    public void addSupport(GroupSupport support) {
        support.assignGroup(this);
        supports.add(support);
    }

    public boolean isOwnedBy(Long organizationId) {
        return organization.getId().equals(organizationId);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}
