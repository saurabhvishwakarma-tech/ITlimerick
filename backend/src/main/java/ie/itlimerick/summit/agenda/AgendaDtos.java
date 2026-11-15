package ie.itlimerick.summit.agenda;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AgendaDtos {

    private AgendaDtos() {
    }

    public record RoomView(UUID id, String name, int sortOrder) {
        static RoomView of(Room r) {
            return new RoomView(r.getId(), r.getName(), r.getSortOrder());
        }
    }

    public record RoomRequest(@NotBlank @Size(max = 100) String name, int sortOrder) {
    }

    public record SpeakerSummary(UUID id, String name, String company, String photoUrl) {
    }

    public record SessionView(UUID id, String title, String description, AgendaSession.Type type,
                              Instant startsAt, Instant endsAt, AgendaSession.Status status,
                              RoomView room, List<SpeakerSummary> speakers) {
    }

    public record SessionRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotNull AgendaSession.Type type,
            @NotNull Instant startsAt,
            Instant endsAt,
            AgendaSession.Status status,
            UUID roomId,
            List<UUID> speakerIds) {
    }
}
