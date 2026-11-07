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
@Table(name = "care_person")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarePerson extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private SupportGroup group;

    @Column(nullable = false, length = 20)
    private String alias;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private boolean applyToJudgement;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "carePerson", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CareItem> items = new ArrayList<>();

    @Builder
    private CarePerson(Organization organization, SupportGroup group, String alias, String memo, boolean applyToJudgement) {
        this.organization = organization;
        this.group = group;
        this.alias = alias;
        this.memo = memo;
        this.applyToJudgement = applyToJudgement;
    }

    public void update(String alias, String memo, boolean applyToJudgement) {
        this.alias = alias;
        this.memo = memo;
        this.applyToJudgement = applyToJudgement;
    }

    public void replaceItems(List<CareItem> newItems) {
        items.clear();
        newItems.forEach(this::addItem);
    }

    public void addItem(CareItem item) {
        item.assignCarePerson(this);
        items.add(item);
    }

    public boolean isOwnedBy(Long organizationId) {
        return organization.getId().equals(organizationId);
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}
