package ie.itlimerick.summit.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A message in an event's attendee chat. Removed messages are kept as a "removed" placeholder. */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /** Null once the author deletes their account: shown as "Former member". */
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected ChatMessage() {
    }

    public ChatMessage(UUID eventId, UUID userId, String body) {
        this.eventId = eventId;
        this.userId = userId;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void remove(UUID by) {
        this.deletedAt = Instant.now();
        this.deletedBy = by;
    }
}
