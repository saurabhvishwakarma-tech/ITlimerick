package ie.itlimerick.summit.chat;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A reaction (heart, thumbs up, party, clap) on an organiser announcement. */
@Entity
@Table(name = "announcement_reaction")
@IdClass(AnnouncementReaction.Key.class)
public class AnnouncementReaction {

    public enum Emoji { HEART, THUMBS_UP, PARTY, CLAP }

    public record Key(UUID announcementId, UUID userId, Emoji emoji) implements Serializable {
        public Key() {
            this(null, null, null);
        }
    }

    @Id
    @Column(name = "announcement_id")
    private UUID announcementId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    private Emoji emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AnnouncementReaction() {
    }

    public AnnouncementReaction(UUID announcementId, UUID userId, Emoji emoji) {
        this.announcementId = announcementId;
        this.userId = userId;
        this.emoji = emoji;
    }

    public UUID getAnnouncementId() { return announcementId; }
    public UUID getUserId() { return userId; }
    public Emoji getEmoji() { return emoji; }
}
