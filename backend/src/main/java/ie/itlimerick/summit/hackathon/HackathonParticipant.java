package ie.itlimerick.summit.hackathon;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Someone who signed up for the hackathon. lookingForTeam = true puts them in the team sorter's pool. */
@Entity
@Table(name = "hackathon_participant")
@IdClass(HackathonParticipant.Key.class)
public class HackathonParticipant {

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

    private String skills;

    @Column(name = "looking_for_team", nullable = false)
    private boolean lookingForTeam = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected HackathonParticipant() {
    }

    public HackathonParticipant(UUID eventId, UUID userId) {
        this.eventId = eventId;
        this.userId = userId;
    }

    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public boolean isLookingForTeam() { return lookingForTeam; }
    public void setLookingForTeam(boolean lookingForTeam) { this.lookingForTeam = lookingForTeam; }
    public Instant getCreatedAt() { return createdAt; }
}
