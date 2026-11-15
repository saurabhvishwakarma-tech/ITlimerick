package ie.itlimerick.summit.hackathon;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One vote per person per event. Changing your vote updates this row. */
@Entity
@Table(name = "vote")
@IdClass(Vote.Key.class)
public class Vote {

    public record Key(UUID eventId, UUID userId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt = Instant.now();

    protected Vote() {
    }

    public Vote(UUID eventId, UUID userId, UUID teamId) {
        this.eventId = eventId;
        this.userId = userId;
        this.teamId = teamId;
    }

    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public Instant getVotedAt() { return votedAt; }
    public void setVotedAt(Instant votedAt) { this.votedAt = votedAt; }
}
