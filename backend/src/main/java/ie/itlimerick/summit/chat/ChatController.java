package ie.itlimerick.summit.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.account.AppUser;
import ie.itlimerick.summit.account.AppUserRepository;
import ie.itlimerick.summit.account.EventMembership;
import ie.itlimerick.summit.account.EventMembershipRepository;
import ie.itlimerick.summit.announcement.Announcement;
import ie.itlimerick.summit.announcement.AnnouncementRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.security.CurrentUser;

/**
 * Attendee chat and reactions on announcements.
 *
 * Chat is off by default and switched on per event by organisers. Only logged-in people can read
 * or post. Phones poll for new messages (no WebSockets), which keeps hosting simple and copes with
 * patchy venue Wi-Fi. Organisers can remove any message; removed messages stay as a placeholder.
 */
@RestController
public class ChatController {

    public record ChatMessageView(UUID id, String authorName, boolean fromOrganiser, boolean mine, String body,
                                  boolean removed, Instant createdAt) {
    }

    public record PostMessageRequest(@NotBlank @Size(max = 1000) String body) {
    }

    private static final int PAGE = 100;
    private static final Duration MIN_GAP = Duration.ofSeconds(3);

    private final ChatMessageRepository messages;
    private final AnnouncementReactionRepository reactions;
    private final AnnouncementRepository announcements;
    private final AppUserRepository users;
    private final EventMembershipRepository memberships;
    private final EventService events;
    private final Map<UUID, Instant> lastPost = new ConcurrentHashMap<>();

    public ChatController(ChatMessageRepository messages, AnnouncementReactionRepository reactions,
                          AnnouncementRepository announcements, AppUserRepository users,
                          EventMembershipRepository memberships, EventService events) {
        this.messages = messages;
        this.reactions = reactions;
        this.announcements = announcements;
        this.users = users;
        this.memberships = memberships;
        this.events = events;
    }

    /** Latest messages, oldest first. Pass ?after=<time of the last message you have> to get only new ones. */
    @GetMapping("/api/v1/events/{slug}/chat")
    @Transactional(readOnly = true)
    public List<ChatMessageView> list(@PathVariable String slug, @RequestParam(required = false) Instant after,
                                      @AuthenticationPrincipal CurrentUser user) {
        if (user == null) {
            throw ApiException.unauthorized("Log in to see the chat");
        }
        Event event = requireChat(slug);
        List<ChatMessage> list;
        if (after != null) {
            list = messages.findByEventIdAndCreatedAtAfterOrderByCreatedAtAsc(event.getId(), after, Limit.of(PAGE));
        } else {
            list = new ArrayList<>(messages.findByEventIdOrderByCreatedAtDesc(event.getId(), Limit.of(PAGE)));
            Collections.reverse(list);
        }
        return views(event.getId(), list, user.id());
    }

    @PostMapping("/api/v1/me/chat")
    @Transactional
    public ResponseEntity<ChatMessageView> post(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user,
                                                @Valid @RequestBody PostMessageRequest req) {
        Event event = requireChat(slug);
        Instant now = Instant.now();
        Instant previous = lastPost.get(user.id());
        if (previous != null && previous.isAfter(now.minus(MIN_GAP))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SLOW_DOWN", "You're sending messages too quickly. Wait a moment.");
        }
        lastPost.put(user.id(), now);
        if (lastPost.size() > 20_000) {
            lastPost.entrySet().removeIf(e -> e.getValue().isBefore(now.minus(MIN_GAP)));
        }
        ChatMessage saved = messages.save(new ChatMessage(event.getId(), user.id(), req.body().strip()));
        return ResponseEntity.status(201).body(views(event.getId(), List.of(saved), user.id()).getFirst());
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/chat/{messageId}")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable UUID eventId, @PathVariable UUID messageId,
                                       @AuthenticationPrincipal CurrentUser user) {
        ChatMessage m = messages.findById(messageId).filter(x -> x.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Message"));
        m.remove(user.id());
        return ResponseEntity.noContent().build();
    }

    /** People can also remove their own messages. */
    @DeleteMapping("/api/v1/me/chat/{messageId}")
    @Transactional
    public ResponseEntity<Void> removeOwn(@PathVariable UUID messageId, @AuthenticationPrincipal CurrentUser user) {
        ChatMessage m = messages.findById(messageId).filter(x -> user.id().equals(x.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Message"));
        m.remove(user.id());
        return ResponseEntity.noContent().build();
    }

    // ---- Reactions on announcements ----

    @PutMapping("/api/v1/me/announcements/{announcementId}/reactions/{emoji}")
    @Transactional
    public ResponseEntity<Void> react(@PathVariable UUID announcementId, @PathVariable AnnouncementReaction.Emoji emoji,
                                      @AuthenticationPrincipal CurrentUser user) {
        Announcement a = announcements.findById(announcementId).orElseThrow(() -> ApiException.notFound("Update"));
        if (!events.require(a.getEventId()).isPublished()) {
            throw ApiException.notFound("Update");
        }
        AnnouncementReaction.Key key = new AnnouncementReaction.Key(announcementId, user.id(), emoji);
        if (!reactions.existsById(key)) {
            reactions.save(new AnnouncementReaction(announcementId, user.id(), emoji));
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/me/announcements/{announcementId}/reactions/{emoji}")
    @Transactional
    public ResponseEntity<Void> unreact(@PathVariable UUID announcementId, @PathVariable AnnouncementReaction.Emoji emoji,
                                        @AuthenticationPrincipal CurrentUser user) {
        reactions.deleteById(new AnnouncementReaction.Key(announcementId, user.id(), emoji));
        return ResponseEntity.noContent().build();
    }

    // ---- Helpers ----

    private Event requireChat(String slug) {
        Event event = events.requirePublished(slug);
        if (!event.isChatEnabled()) {
            throw ApiException.notFound("Chat");
        }
        return event;
    }

    private List<ChatMessageView> views(UUID eventId, List<ChatMessage> list, UUID viewerId) {
        Set<UUID> authorIds = list.stream().map(ChatMessage::getUserId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AppUser> authors = users.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Set<UUID> organisers = memberships.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .filter(m -> m.getRole() == EventMembership.Role.ORGANISER)
                .map(EventMembership::getUserId).collect(Collectors.toSet());
        return list.stream().map(m -> {
            AppUser author = m.getUserId() == null ? null : authors.get(m.getUserId());
            boolean organiser = author != null && (author.isPlatformAdmin() || organisers.contains(author.getId()));
            boolean removed = m.getDeletedAt() != null;
            return new ChatMessageView(m.getId(), author == null ? "Former member" : author.getDisplayName(), organiser,
                    m.getUserId() != null && m.getUserId().equals(viewerId), removed ? null : m.getBody(), removed,
                    m.getCreatedAt());
        }).toList();
    }
}
