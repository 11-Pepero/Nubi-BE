package dev.hidly.nubi.notification.data.repository;

import dev.hidly.nubi.notification.data.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByOrganizationId(Long organizationId, Pageable pageable);

    long countByOrganizationIdAndReadAtIsNull(Long organizationId);
}
