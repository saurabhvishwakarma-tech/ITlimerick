package ie.itlimerick.summit.event;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.account.EventMembership;
import ie.itlimerick.summit.account.EventMembershipRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.EventDtos.EventRequest;
import ie.itlimerick.summit.event.EventDtos.EventView;
import ie.itlimerick.summit.security.CurrentUser;

@Service
public class EventService {

    private final EventRepository events;
    private final EventMembershipRepository memberships;

    public EventService(EventRepository events, EventMembershipRepository memberships) {
        this.events = events;
        this.memberships = memberships;
    }

    /** Used by every public endpoint: drafts are invisible to the public. */
    @Transactional(readOnly = true)
    public Event requirePublished(String slug) {
        return events.findBySlug(slug).filter(Event::isPublished).orElseThrow(() -> ApiException.notFound("Event"));
    }

    @Transactional(readOnly = true)
    public Event require(UUID id) {
        return events.findById(id).orElseThrow(() -> ApiException.notFound("Event"));
    }

    @Transactional(readOnly = true)
    public List<EventView> listPublished() {
        return events.findByStatusOrderByStartsAtDesc(Event.Status.PUBLISHED).stream().map(EventView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<EventView> listManageable(CurrentUser user) {
        List<Event> result;
        if (user.platformAdmin()) {
            result = events.findAllByOrderByStartsAtDesc();
        } else {
            List<UUID> ids = memberships.findByUserId(user.id()).stream()
                    .filter(m -> m.getRole() == EventMembership.Role.ORGANISER)
                    .map(EventMembership::getEventId).toList();
            result = events.findByIdInOrderByStartsAtDesc(ids);
        }
        return result.stream().map(EventView::of).toList();
    }

    @Transactional
    public EventView create(EventRequest req) {
        if (events.existsBySlug(req.slug())) {
            throw ApiException.conflict("That web address (slug) is already used by another event");
        }
        Event event = new Event();
        apply(event, req);
        return EventView.of(events.save(event));
    }

    @Transactional
    public EventView update(UUID id, EventRequest req) {
        Event event = require(id);
        if (!event.getSlug().equals(req.slug()) && events.existsBySlug(req.slug())) {
            throw ApiException.conflict("That web address (slug) is already used by another event");
        }
        apply(event, req);
        return EventView.of(event);
    }

    @Transactional
    public void delete(UUID id) {
        events.delete(require(id));
    }

    private void apply(Event e, EventRequest req) {
        if (req.startsAt() != null && req.endsAt() != null && req.endsAt().isBefore(req.startsAt())) {
            throw ApiException.badRequest("The event can't end before it starts");
        }
        String zone = req.timezone() == null || req.timezone().isBlank() ? "Europe/Dublin" : req.timezone().trim();
        try {
            ZoneId.of(zone);
        } catch (DateTimeException ex) {
            throw ApiException.badRequest("Unknown timezone: " + zone);
        }
        e.setSlug(req.slug());
        e.setName(req.name().trim());
        e.setTagline(blankToNull(req.tagline()));
        e.setDescription(blankToNull(req.description()));
        e.setVenueName(blankToNull(req.venueName()));
        e.setVenueAddress(blankToNull(req.venueAddress()));
        e.setStartsAt(req.startsAt());
        e.setEndsAt(req.endsAt());
        e.setTimezone(zone);
        e.setStatus(req.status());
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
