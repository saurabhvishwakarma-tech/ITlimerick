package ie.itlimerick.summit.hackathon;

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

@Entity
@Table(name = "team_member")
@IdClass(TeamMember.Key.class)
public class TeamMember {

    public enum Role { LEAD, MEMBER }

    public record Key(UUID teamId, UUID userId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "team_id")
    private UUID teamId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected TeamMember() {
    }

    public TeamMember(UUID teamId, UUID userId, UUID eventId, Role role) {
        this.teamId = teamId;
        this.userId = userId;
        this.eventId = eventId;
        this.role = role;
    }

    public UUID getTeamId() { return teamId; }
    public UUID getUserId() { return userId; }
    public UUID getEventId() { return eventId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
}
