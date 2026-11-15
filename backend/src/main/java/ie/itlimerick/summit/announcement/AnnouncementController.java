package ie.itlimerick.summit.announcement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Limit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.chat.AnnouncementReaction;
import ie.itlimerick.summit.chat.AnnouncementReactionRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.security.CurrentUser;

import static ie.itlimerick.summit.event.EventService.blankToNull;

/**
 * Live announcements. Phones poll the public list every ~30 seconds; the ETag filter turns
 * unchanged responses into a cheap 304, so polling stays light even with many attendees.
 */
@RestController
public class AnnouncementController {

    public record AnnouncementView(UUID id, String title, String body, Announcement.Priority priority, boolean pinned,
                                   Instant publishedAt, Instant updatedAt,
                                   Map<AnnouncementReaction.Emoji, Long> reactions,
                                   List<AnnouncementReaction.Emoji> myReactions) {
        static AnnouncementView of(Announcement a) {
            return new AnnouncementView(a.getId(), a.getTitle(), a.getBody(), a.getPriority(), a.isPinned(),
                    a.getPublishedAt(), a.getUpdatedAt(), Map.of(), List.of());
        }
    }

    public record AnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String body,
            Announcement.Priority priority,
            boolean pinned,
            Instant publishedAt) {
    }

    private static final int PUBLIC_LIMIT = 50;

    private final AnnouncementRepository announcements;
    private final AnnouncementReactionRepository reactions;
    private final EventService events;

    public AnnouncementController(AnnouncementRepository announcements, AnnouncementReactionRepository reactions,
                                  EventService events) {
        this.announcements = announcements;
        this.reactions = reactions;
        this.events = events;
    }

    @GetMapping("/api/v1/events/{slug}/announcements")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AnnouncementView>> live(@PathVariable String slug, @AuthenticationPrincipal CurrentUser user) {
        UUID eventId = events.requirePublished(slug).getId();
        List<Announcement> live = announcements.findLive(eventId, Instant.now(), Limit.of(PUBLIC_LIMIT));
        List<AnnouncementReaction> all = reactions.findByAnnouncementIdIn(live.stream().map(Announcement::getId).toList());
        List<AnnouncementView> body = live.stream().map(a -> {
            Map<AnnouncementReaction.Emoji, Long> counts = new EnumMap<>(AnnouncementReaction.Emoji.class);
            List<AnnouncementReaction.Emoji> mine = new ArrayList<>();
            all.stream().filter(r -> r.getAnnouncementId().equals(a.getId())).forEach(r -> {
                counts.merge(r.getEmoji(), 1L, Long::sum);
                if (user != null && r.getUserId().equals(user.id())) {
                    mine.add(r.getEmoji());
                }
            });
            return new AnnouncementView(a.getId(), a.getTitle(), a.getBody(), a.getPriority(), a.isPinned(),
                    a.getPublishedAt(), a.getUpdatedAt(), counts, mine);
        }).toList();
        // "private" because the response includes the viewer's own reactions.
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate()).body(body);
    }

    @GetMapping("/api/v1/admin/events/{eventId}/announcements")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional(readOnly = true)
    public List<AnnouncementView> adminList(@PathVariable UUID eventId) {
        return announcements.findByEventIdOrderByPublishedAtDesc(eventId).stream().map(AnnouncementView::of).toList();
    }

    @PostMapping("/api/v1/admin/events/{eventId}/announcements")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public ResponseEntity<AnnouncementView> create(@PathVariable UUID eventId, @Valid @RequestBody AnnouncementRequest req,
                                                   @AuthenticationPrincipal CurrentUser user) {
        events.require(eventId);
        Announcement a = new Announcement();
        a.setEventId(eventId);
        a.setCreatedBy(user.id());
        apply(a, req);
        return ResponseEntity.status(201).body(AnnouncementView.of(announcements.save(a)));
    }

    @PutMapping("/api/v1/admin/events/{eventId}/announcements/{announcementId}")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public AnnouncementView update(@PathVariable UUID eventId, @PathVariable UUID announcementId,
                                   @Valid @RequestBody AnnouncementRequest req) {
        Announcement a = require(eventId, announcementId);
        apply(a, req);
        return AnnouncementView.of(a);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/announcements/{announcementId}")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID announcementId) {
        announcements.delete(require(eventId, announcementId));
        return ResponseEntity.noContent().build();
    }

    private Announcement require(UUID eventId, UUID id) {
        return announcements.findById(id).filter(a -> a.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Announcement"));
    }

    private void apply(Announcement a, AnnouncementRequest req) {
        a.setTitle(req.title().trim());
        a.setBody(blankToNull(req.body()));
        a.setPriority(req.priority() == null ? Announcement.Priority.NORMAL : req.priority());
        a.setPinned(req.pinned());
        if (req.publishedAt() != null) {
            a.setPublishedAt(req.publishedAt());
        }
    }
}
