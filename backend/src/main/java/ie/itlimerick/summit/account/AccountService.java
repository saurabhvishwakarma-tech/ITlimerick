package ie.itlimerick.summit.account;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.agenda.AgendaSession;
import ie.itlimerick.summit.agenda.AgendaSessionRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.config.AppProperties;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventRepository;
import ie.itlimerick.summit.schedule.Bookmark;
import ie.itlimerick.summit.schedule.BookmarkRepository;
import ie.itlimerick.summit.security.AuthSessionRepository;
import ie.itlimerick.summit.speaker.Speaker;
import ie.itlimerick.summit.speaker.SpeakerRepository;

/** Profile, consents and the GDPR rights (export and delete) for the logged-in user. */
@Service
public class AccountService {

    public record MembershipView(UUID id, UUID eventId, String eventSlug, String eventName, EventMembership.Role role) {
    }

    public record ProfileView(UUID id, String email, String displayName, boolean platformAdmin, boolean marketingConsent,
                              Instant createdAt, List<MembershipView> memberships) {
    }

    public record ExportView(Instant exportedAt, Map<String, Object> account, List<MembershipView> memberships,
                             List<Map<String, Object>> savedSessions, List<Map<String, Object>> consents,
                             List<Map<String, Object>> linkedSpeakerProfiles, long activeLoginSessions) {
    }

    public record MemberView(UUID id, UUID userId, String email, String displayName, EventMembership.Role role,
                             Instant createdAt) {
    }

    private final AppUserRepository users;
    private final EventMembershipRepository memberships;
    private final ConsentRecordRepository consents;
    private final BookmarkRepository bookmarks;
    private final AgendaSessionRepository sessions;
    private final SpeakerRepository speakers;
    private final EventRepository events;
    private final AuthSessionRepository authSessions;
    private final AppProperties props;

    public AccountService(AppUserRepository users, EventMembershipRepository memberships,
                          ConsentRecordRepository consents, BookmarkRepository bookmarks,
                          AgendaSessionRepository sessions, SpeakerRepository speakers, EventRepository events,
                          AuthSessionRepository authSessions, AppProperties props) {
        this.users = users;
        this.memberships = memberships;
        this.consents = consents;
        this.bookmarks = bookmarks;
        this.sessions = sessions;
        this.speakers = speakers;
        this.events = events;
        this.authSessions = authSessions;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public ProfileView profile(UUID userId) {
        AppUser user = requireUser(userId);
        boolean marketing = consents.findFirstByUserIdAndTypeOrderByRecordedAtDesc(userId, ConsentRecord.Type.MARKETING)
                .map(ConsentRecord::isGranted).orElse(false);
        return new ProfileView(user.getId(), user.getEmail(), user.getDisplayName(), user.isPlatformAdmin(), marketing,
                user.getCreatedAt(), membershipViews(memberships.findByUserId(userId)));
    }

    @Transactional
    public ProfileView updateDisplayName(UUID userId, String displayName) {
        requireUser(userId).setDisplayName(displayName.trim());
        return profile(userId);
    }

    @Transactional
    public ProfileView setMarketingConsent(UUID userId, boolean granted) {
        requireUser(userId);
        consents.save(new ConsentRecord(userId, ConsentRecord.Type.MARKETING, props.privacyNoticeVersion(), granted));
        return profile(userId);
    }

    /** GDPR right of access: everything we hold about this user, as JSON. */
    @Transactional(readOnly = true)
    public ExportView export(UUID userId) {
        AppUser user = requireUser(userId);
        List<Bookmark> saved = bookmarks.findByUserId(userId);
        Map<UUID, AgendaSession> sessionById = sessions.findByIdIn(saved.stream().map(Bookmark::getSessionId).toList())
                .stream().collect(Collectors.toMap(AgendaSession::getId, Function.identity()));
        List<Map<String, Object>> savedSessions = saved.stream().map(b -> Map.<String, Object>of(
                "sessionId", b.getSessionId(),
                "title", sessionById.containsKey(b.getSessionId()) ? sessionById.get(b.getSessionId()).getTitle() : "",
                "savedAt", b.getCreatedAt())).toList();
        List<Map<String, Object>> consentList = consents.findByUserIdOrderByRecordedAtAsc(userId).stream()
                .map(c -> Map.<String, Object>of("type", c.getType(), "granted", c.isGranted(),
                        "privacyNoticeVersion", c.getNoticeVersion(), "recordedAt", c.getRecordedAt()))
                .toList();
        List<Map<String, Object>> speakerProfiles = speakers.findByUserId(userId).stream()
                .map(s -> Map.<String, Object>of("speakerId", s.getId(), "name", s.getName(), "eventId", s.getEventId()))
                .toList();
        Map<String, Object> account = new java.util.LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("email", user.getEmail());
        account.put("displayName", user.getDisplayName());
        account.put("createdAt", user.getCreatedAt());
        account.put("lastLoginAt", user.getLastLoginAt());
        return new ExportView(Instant.now(), account, membershipViews(memberships.findByUserId(userId)),
                savedSessions, consentList, speakerProfiles, authSessions.countByUserId(userId));
    }

    /**
     * GDPR right to erasure. Deletes the account and everything linked to it (sessions, bookmarks,
     * memberships, consents cascade in the database). Speaker profiles and announcements stay but
     * are unlinked from the account.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        AppUser user = requireUser(userId);
        for (Speaker s : speakers.findByUserId(userId)) {
            s.setUserId(null);
        }
        users.delete(user);
    }

    // ---- Event members (admin) ----

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID eventId) {
        List<EventMembership> list = memberships.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<UUID, AppUser> userById = users.findAllById(list.stream().map(EventMembership::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return list.stream().map(m -> {
            AppUser u = userById.get(m.getUserId());
            return new MemberView(m.getId(), m.getUserId(), u == null ? "" : u.getEmail(),
                    u == null ? "" : u.getDisplayName(), m.getRole(), m.getCreatedAt());
        }).toList();
    }

    @Transactional
    public MemberView addMember(UUID eventId, String email, EventMembership.Role role) {
        if (!events.existsById(eventId)) {
            throw ApiException.notFound("Event");
        }
        AppUser user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.badRequest("No account uses that email yet. Ask them to sign up first."));
        if (memberships.existsByUserIdAndEventIdAndRole(user.getId(), eventId, role)) {
            throw ApiException.conflict("They already have that role for this event");
        }
        EventMembership m = memberships.save(new EventMembership(user.getId(), eventId, role));
        return new MemberView(m.getId(), user.getId(), user.getEmail(), user.getDisplayName(), m.getRole(), m.getCreatedAt());
    }

    @Transactional
    public void removeMember(UUID eventId, UUID membershipId) {
        EventMembership m = memberships.findById(membershipId).filter(x -> x.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Member"));
        memberships.delete(m);
    }

    private AppUser requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    private List<MembershipView> membershipViews(List<EventMembership> list) {
        Map<UUID, Event> eventById = events.findAllById(list.stream().map(EventMembership::getEventId).toList()).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        return list.stream().map(m -> {
            Event e = eventById.get(m.getEventId());
            return new MembershipView(m.getId(), m.getEventId(), e == null ? null : e.getSlug(),
                    e == null ? null : e.getName(), m.getRole());
        }).toList();
    }
}
