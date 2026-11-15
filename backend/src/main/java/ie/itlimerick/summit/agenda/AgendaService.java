package ie.itlimerick.summit.agenda;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.agenda.AgendaDtos.RoomRequest;
import ie.itlimerick.summit.agenda.AgendaDtos.RoomView;
import ie.itlimerick.summit.agenda.AgendaDtos.SessionRequest;
import ie.itlimerick.summit.agenda.AgendaDtos.SessionView;
import ie.itlimerick.summit.agenda.AgendaDtos.SpeakerSummary;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.speaker.Speaker;
import ie.itlimerick.summit.speaker.SpeakerRepository;

@Service
public class AgendaService {

    private final AgendaSessionRepository sessions;
    private final RoomRepository rooms;
    private final SpeakerRepository speakers;
    private final EventService events;

    public AgendaService(AgendaSessionRepository sessions, RoomRepository rooms, SpeakerRepository speakers,
                         EventService events) {
        this.sessions = sessions;
        this.rooms = rooms;
        this.speakers = speakers;
        this.events = events;
    }

    // ---- Public ----

    @Transactional(readOnly = true)
    public List<SessionView> publicAgenda(String eventSlug) {
        Event event = events.requirePublished(eventSlug);
        return toViews(event.getId(), sessions.findByEventIdOrderByStartsAtAscTitleAsc(event.getId()), true);
    }

    @Transactional(readOnly = true)
    public SessionView publicSession(UUID sessionId) {
        AgendaSession session = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        Event event = events.require(session.getEventId());
        if (!event.isPublished()) {
            throw ApiException.notFound("Session");
        }
        return toViews(event.getId(), List.of(session), true).getFirst();
    }

    @Transactional(readOnly = true)
    public List<RoomView> publicRooms(String eventSlug) {
        Event event = events.requirePublished(eventSlug);
        return listRooms(event.getId());
    }

    // ---- Admin: sessions ----

    @Transactional(readOnly = true)
    public List<SessionView> adminAgenda(UUID eventId) {
        events.require(eventId);
        return toViews(eventId, sessions.findByEventIdOrderByStartsAtAscTitleAsc(eventId), false);
    }

    @Transactional
    public SessionView createSession(UUID eventId, SessionRequest req) {
        events.require(eventId);
        AgendaSession s = new AgendaSession();
        s.setEventId(eventId);
        apply(s, req);
        return toViews(eventId, List.of(sessions.save(s)), false).getFirst();
    }

    @Transactional
    public SessionView updateSession(UUID eventId, UUID sessionId, SessionRequest req) {
        AgendaSession s = requireSession(eventId, sessionId);
        apply(s, req);
        return toViews(eventId, List.of(s), false).getFirst();
    }

    @Transactional
    public void deleteSession(UUID eventId, UUID sessionId) {
        sessions.delete(requireSession(eventId, sessionId));
    }

    // ---- Admin: rooms ----

    @Transactional(readOnly = true)
    public List<RoomView> listRooms(UUID eventId) {
        return rooms.findByEventIdOrderBySortOrderAscNameAsc(eventId).stream().map(RoomView::of).toList();
    }

    @Transactional
    public RoomView createRoom(UUID eventId, RoomRequest req) {
        events.require(eventId);
        Room room = new Room();
        room.setEventId(eventId);
        room.setName(req.name().trim());
        room.setSortOrder(req.sortOrder());
        return RoomView.of(rooms.save(room));
    }

    @Transactional
    public RoomView updateRoom(UUID eventId, UUID roomId, RoomRequest req) {
        Room room = requireRoom(eventId, roomId);
        room.setName(req.name().trim());
        room.setSortOrder(req.sortOrder());
        return RoomView.of(room);
    }

    @Transactional
    public void deleteRoom(UUID eventId, UUID roomId) {
        rooms.delete(requireRoom(eventId, roomId));
    }

    // ---- Helpers ----

    private void apply(AgendaSession s, SessionRequest req) {
        if (req.endsAt() != null && req.endsAt().isBefore(req.startsAt())) {
            throw ApiException.badRequest("A session can't end before it starts");
        }
        if (req.roomId() != null) {
            requireRoom(s.getEventId(), req.roomId());
        }
        List<UUID> speakerIds = req.speakerIds() == null ? List.of() : req.speakerIds().stream().distinct().toList();
        if (!speakerIds.isEmpty()) {
            List<Speaker> found = speakers.findAllById(speakerIds);
            boolean allInEvent = found.size() == speakerIds.size()
                    && found.stream().allMatch(sp -> sp.getEventId().equals(s.getEventId()));
            if (!allInEvent) {
                throw ApiException.badRequest("One or more speakers don't belong to this event");
            }
        }
        s.setTitle(req.title().trim());
        s.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        s.setType(req.type());
        s.setStartsAt(req.startsAt());
        s.setEndsAt(req.endsAt());
        s.setStatus(req.status() == null ? AgendaSession.Status.SCHEDULED : req.status());
        s.setRoomId(req.roomId());
        s.getSpeakerIds().clear();
        s.getSpeakerIds().addAll(speakerIds);
    }

    private AgendaSession requireSession(UUID eventId, UUID sessionId) {
        return sessions.findById(sessionId)
                .filter(s -> s.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Session"));
    }

    private Room requireRoom(UUID eventId, UUID roomId) {
        return rooms.findById(roomId)
                .filter(r -> r.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.badRequest("That room doesn't belong to this event"));
    }

    private List<SessionView> toViews(UUID eventId, Collection<AgendaSession> list, boolean publicOnly) {
        Map<UUID, Room> roomById = rooms.findByEventIdOrderBySortOrderAscNameAsc(eventId).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        Map<UUID, Speaker> speakerById = speakers.findByEventIdOrderBySortOrderAscNameAsc(eventId).stream()
                .filter(sp -> !publicOnly || sp.isPublished())
                .collect(Collectors.toMap(Speaker::getId, Function.identity()));
        return list.stream().map(s -> {
            Room room = s.getRoomId() == null ? null : roomById.get(s.getRoomId());
            List<SpeakerSummary> sessionSpeakers = s.getSpeakerIds().stream()
                    .map(speakerById::get)
                    .filter(Objects::nonNull)
                    .map(sp -> new SpeakerSummary(sp.getId(), sp.getName(), sp.getCompany(), sp.getPhotoUrl()))
                    .toList();
            return new SessionView(s.getId(), s.getTitle(), s.getDescription(), s.getType(), s.getStartsAt(),
                    s.getEndsAt(), s.getStatus(), room == null ? null : RoomView.of(room), sessionSpeakers);
        }).toList();
    }
}
