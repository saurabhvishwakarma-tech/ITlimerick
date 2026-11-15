package ie.itlimerick.summit.speaker;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.agenda.AgendaSessionRepository;
import ie.itlimerick.summit.agenda.Room;
import ie.itlimerick.summit.agenda.RoomRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerDetail;
import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerRequest;
import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerSessionView;
import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerView;

import static ie.itlimerick.summit.event.EventService.blankToNull;

@Service
public class SpeakerService {

    private final SpeakerRepository speakers;
    private final AgendaSessionRepository sessions;
    private final RoomRepository rooms;
    private final EventService events;

    public SpeakerService(SpeakerRepository speakers, AgendaSessionRepository sessions, RoomRepository rooms,
                          EventService events) {
        this.speakers = speakers;
        this.sessions = sessions;
        this.rooms = rooms;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<SpeakerView> publicList(String eventSlug) {
        Event event = events.requirePublished(eventSlug);
        return speakers.findByEventIdAndPublishedTrueOrderBySortOrderAscNameAsc(event.getId()).stream()
                .map(SpeakerView::of).toList();
    }

    @Transactional(readOnly = true)
    public SpeakerDetail publicDetail(UUID speakerId) {
        Speaker speaker = speakers.findById(speakerId).filter(Speaker::isPublished)
                .orElseThrow(() -> ApiException.notFound("Speaker"));
        Event event = events.require(speaker.getEventId());
        if (!event.isPublished()) {
            throw ApiException.notFound("Speaker");
        }
        Map<UUID, Room> roomById = rooms.findByEventIdOrderBySortOrderAscNameAsc(event.getId()).stream()
                .collect(Collectors.toMap(Room::getId, Function.identity()));
        List<SpeakerSessionView> talks = sessions.findBySpeakerId(speakerId).stream()
                .map(s -> new SpeakerSessionView(s.getId(), s.getTitle(), s.getStartsAt(), s.getEndsAt(),
                        s.getRoomId() == null || !roomById.containsKey(s.getRoomId()) ? null : roomById.get(s.getRoomId()).getName()))
                .toList();
        return new SpeakerDetail(SpeakerView.of(speaker), event.getSlug(), talks);
    }

    @Transactional(readOnly = true)
    public List<SpeakerView> adminList(UUID eventId) {
        events.require(eventId);
        return speakers.findByEventIdOrderBySortOrderAscNameAsc(eventId).stream().map(SpeakerView::of).toList();
    }

    @Transactional
    public SpeakerView create(UUID eventId, SpeakerRequest req) {
        events.require(eventId);
        Speaker s = new Speaker();
        s.setEventId(eventId);
        apply(s, req);
        return SpeakerView.of(speakers.save(s));
    }

    @Transactional
    public SpeakerView update(UUID eventId, UUID speakerId, SpeakerRequest req) {
        Speaker s = require(eventId, speakerId);
        apply(s, req);
        return SpeakerView.of(s);
    }

    @Transactional
    public void delete(UUID eventId, UUID speakerId) {
        speakers.delete(require(eventId, speakerId));
    }

    private Speaker require(UUID eventId, UUID speakerId) {
        return speakers.findById(speakerId).filter(s -> s.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Speaker"));
    }

    private void apply(Speaker s, SpeakerRequest req) {
        s.setName(req.name().trim());
        s.setHeadline(blankToNull(req.headline()));
        s.setCompany(blankToNull(req.company()));
        s.setBio(blankToNull(req.bio()));
        s.setPhotoUrl(blankToNull(req.photoUrl()));
        s.setLinkedinUrl(blankToNull(req.linkedinUrl()));
        s.setWebsiteUrl(blankToNull(req.websiteUrl()));
        s.setPublished(req.published());
        s.setSortOrder(req.sortOrder());
    }
}
