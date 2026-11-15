package ie.itlimerick.summit.schedule;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.agenda.AgendaSession;
import ie.itlimerick.summit.agenda.AgendaSessionRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.security.CurrentUser;

/** "My schedule": the sessions a logged-in user has bookmarked. */
@RestController
@RequestMapping("/api/v1/me/schedule")
public class ScheduleController {

    private final BookmarkRepository bookmarks;
    private final AgendaSessionRepository sessions;
    private final EventService events;

    public ScheduleController(BookmarkRepository bookmarks, AgendaSessionRepository sessions, EventService events) {
        this.bookmarks = bookmarks;
        this.sessions = sessions;
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UUID> list(@AuthenticationPrincipal CurrentUser user, @RequestParam("event") String eventSlug) {
        UUID eventId = events.requirePublished(eventSlug).getId();
        return bookmarks.findSessionIds(user.id(), eventId);
    }

    @PutMapping("/{sessionId}")
    @Transactional
    public ResponseEntity<Void> add(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID sessionId) {
        AgendaSession session = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        if (!events.require(session.getEventId()).isPublished()) {
            throw ApiException.notFound("Session");
        }
        Bookmark.Key key = new Bookmark.Key(user.id(), sessionId);
        if (!bookmarks.existsById(key)) {
            bookmarks.save(new Bookmark(user.id(), sessionId));
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessionId}")
    @Transactional
    public ResponseEntity<Void> remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID sessionId) {
        bookmarks.deleteById(new Bookmark.Key(user.id(), sessionId));
        return ResponseEntity.noContent().build();
    }
}
