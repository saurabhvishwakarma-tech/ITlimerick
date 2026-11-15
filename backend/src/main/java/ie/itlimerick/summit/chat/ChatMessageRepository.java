package ie.itlimerick.summit.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByEventIdOrderByCreatedAtDesc(UUID eventId, Limit limit);

    List<ChatMessage> findByEventIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID eventId, Instant after, Limit limit);

    List<ChatMessage> findByUserId(UUID userId);
}
