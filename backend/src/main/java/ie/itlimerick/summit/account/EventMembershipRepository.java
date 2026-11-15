package ie.itlimerick.summit.account;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMembershipRepository extends JpaRepository<EventMembership, UUID> {

    List<EventMembership> findByUserId(UUID userId);

    List<EventMembership> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    boolean existsByUserIdAndEventIdAndRole(UUID userId, UUID eventId, EventMembership.Role role);
}
