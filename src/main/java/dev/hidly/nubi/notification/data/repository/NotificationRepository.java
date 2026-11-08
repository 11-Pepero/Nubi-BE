package dev.hidly.nubi.notification.data.repository;

import dev.hidly.nubi.notification.data.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByOrganizationId(Long organizationId, Pageable pageable);

    long countByOrganizationIdAndReadAtIsNull(Long organizationId);

    Optional<Notification> findByIdAndOrganizationId(Long id, Long organizationId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.organization.id = :organizationId and n.readAt is null")
    int markAllRead(@Param("organizationId") Long organizationId, @Param("now") LocalDateTime now);
}
