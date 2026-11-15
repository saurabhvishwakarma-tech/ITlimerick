package ie.itlimerick.summit.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "event")
public class Event {

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String tagline;

    private String description;

    @Column(name = "venue_name")
    private String venueName;

    @Column(name = "venue_address")
    private String venueAddress;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false)
    private String timezone = "Europe/Dublin";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "hackathon_enabled", nullable = false)
    private boolean hackathonEnabled;

    @Column(name = "team_max_size", nullable = false)
    private int teamMaxSize = 5;

    @Column(name = "voting_opens_at")
    private Instant votingOpensAt;

    @Column(name = "voting_closes_at")
    private Instant votingClosesAt;

    @Column(name = "voting_results_public", nullable = false)
    private boolean votingResultsPublic;

    @Column(name = "chat_enabled", nullable = false)
    private boolean chatEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public String getVenueAddress() { return venueAddress; }
    public void setVenueAddress(String venueAddress) { this.venueAddress = venueAddress; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isPublished() { return status == Status.PUBLISHED; }
    public boolean isHackathonEnabled() { return hackathonEnabled; }
    public void setHackathonEnabled(boolean hackathonEnabled) { this.hackathonEnabled = hackathonEnabled; }
    public int getTeamMaxSize() { return teamMaxSize; }
    public void setTeamMaxSize(int teamMaxSize) { this.teamMaxSize = teamMaxSize; }
    public Instant getVotingOpensAt() { return votingOpensAt; }
    public void setVotingOpensAt(Instant votingOpensAt) { this.votingOpensAt = votingOpensAt; }
    public Instant getVotingClosesAt() { return votingClosesAt; }
    public void setVotingClosesAt(Instant votingClosesAt) { this.votingClosesAt = votingClosesAt; }
    public boolean isVotingResultsPublic() { return votingResultsPublic; }
    public void setVotingResultsPublic(boolean votingResultsPublic) { this.votingResultsPublic = votingResultsPublic; }
    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean chatEnabled) { this.chatEnabled = chatEnabled; }

    /** Voting is open between the two times the organisers set. Both must be set. */
    public boolean isVotingOpen(Instant now) {
        return votingOpensAt != null && votingClosesAt != null && !now.isBefore(votingOpensAt) && now.isBefore(votingClosesAt);
    }

    public boolean isVotingClosed(Instant now) {
        return votingClosesAt != null && !now.isBefore(votingClosesAt);
    }
}
