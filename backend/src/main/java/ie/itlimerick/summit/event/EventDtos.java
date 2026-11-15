package ie.itlimerick.summit.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventView(UUID id, String slug, String name, String tagline, String description,
                            String venueName, String venueAddress, Instant startsAt, Instant endsAt,
                            String timezone, Event.Status status, boolean hackathonEnabled, int teamMaxSize,
                            Instant votingOpensAt, Instant votingClosesAt, boolean votingResultsPublic,
                            boolean chatEnabled) {

        public static EventView of(Event e) {
            return new EventView(e.getId(), e.getSlug(), e.getName(), e.getTagline(), e.getDescription(),
                    e.getVenueName(), e.getVenueAddress(), e.getStartsAt(), e.getEndsAt(), e.getTimezone(), e.getStatus(),
                    e.isHackathonEnabled(), e.getTeamMaxSize(), e.getVotingOpensAt(), e.getVotingClosesAt(),
                    e.isVotingResultsPublic(), e.isChatEnabled());
        }
    }

    public record EventRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "Use lowercase letters, numbers and dashes")
            @Size(max = 60) String slug,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 200) String tagline,
            @Size(max = 10000) String description,
            @Size(max = 200) String venueName,
            @Size(max = 300) String venueAddress,
            Instant startsAt,
            Instant endsAt,
            @Size(max = 60) String timezone,
            @NotNull Event.Status status,
            boolean hackathonEnabled,
            @Min(0) @Max(50) Integer teamMaxSize,
            Instant votingOpensAt,
            Instant votingClosesAt,
            boolean votingResultsPublic,
            boolean chatEnabled) {
    }
}
