package ie.itlimerick.summit.schedule;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A session a user has saved to "My schedule". */
@Entity
@Table(name = "bookmark")
@IdClass(Bookmark.Key.class)
public class Bookmark {

    public record Key(UUID userId, UUID sessionId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Bookmark() {
    }

    public Bookmark(UUID userId, UUID sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
