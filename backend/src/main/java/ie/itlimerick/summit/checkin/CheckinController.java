package ie.itlimerick.summit.checkin;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.security.CurrentUser;

/**
 * QR check-in at the door.
 *
 * Every logged-in attendee has a ticket per event: a short code shown as a QR code on their phone
 * (and as text, in case the camera won't cooperate). Organisers scan it from the admin panel, or
 * type the code, or check someone in by email if they never opened the app.
 */
@RestController
public class CheckinController {

    public record TicketView(String eventName, String code, String qrText, Instant checkedInAt) {
    }

    public record CheckinRequest(@NotBlank @Size(max = 100) String code) {
    }

    public record CheckinByEmailRequest(@NotBlank @Email String email) {
    }

    public record CheckinResult(UUID userId, String name, String email, Instant checkedInAt, boolean alreadyCheckedIn) {
    }

    public record AttendeeView(UUID userId, String name, String email, String code, Instant checkedInAt) {
    }

    public record CheckinSummary(int tickets, int checkedIn, List<AttendeeView> attendees) {
    }

    /** Printed in front of the code inside the QR, so a random QR code isn't mistaken for a ticket. */
    static final String QR_PREFIX = "ITLSUMMIT:";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final EventService events;

    public CheckinController(JdbcClient jdbc, EventService events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @GetMapping("/api/v1/me/ticket")
    @Transactional
    public TicketView myTicket(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user) {
        Event event = events.requirePublished(slug);
        ensureTicket(event.getId(), user.id());
        Map<String, Object> row = jdbc.sql("SELECT code, checked_in_at FROM event_ticket WHERE event_id = ? AND user_id = ?")
                .params(event.getId(), user.id()).query().singleRow();
        String code = (String) row.get("code");
        return new TicketView(event.getName(), code, QR_PREFIX + code, instant(row.get("checked_in_at")));
    }

    @PostMapping("/api/v1/admin/events/{eventId}/checkins")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public CheckinResult checkIn(@PathVariable UUID eventId, @Valid @RequestBody CheckinRequest req,
                                 @AuthenticationPrincipal CurrentUser user) {
        String code = normalise(req.code());
        UUID attendee = jdbc.sql("SELECT user_id FROM event_ticket WHERE event_id = ? AND code = ?")
                .params(eventId, code).query(UUID.class).optional()
                .orElseThrow(() -> ApiException.notFound("Ticket"));
        return markCheckedIn(eventId, attendee, user.id());
    }

    /** For people who never opened their ticket (or don't have their phone): find them by email. */
    @PostMapping("/api/v1/admin/events/{eventId}/checkins/by-email")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public CheckinResult checkInByEmail(@PathVariable UUID eventId, @Valid @RequestBody CheckinByEmailRequest req,
                                        @AuthenticationPrincipal CurrentUser user) {
        events.require(eventId);
        UUID attendee = jdbc.sql("SELECT id FROM app_user WHERE lower(email) = lower(?)")
                .param(req.email().trim()).query(UUID.class).optional()
                .orElseThrow(() -> ApiException.notFound("Account with that email"));
        ensureTicket(eventId, attendee);
        return markCheckedIn(eventId, attendee, user.id());
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/checkins/{userId}")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public ResponseEntity<Void> undo(@PathVariable UUID eventId, @PathVariable UUID userId) {
        jdbc.sql("UPDATE event_ticket SET checked_in_at = NULL, checked_in_by = NULL WHERE event_id = ? AND user_id = ?")
                .params(eventId, userId).update();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/admin/events/{eventId}/checkins")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional(readOnly = true)
    public CheckinSummary list(@PathVariable UUID eventId) {
        List<AttendeeView> attendees = jdbc.sql("""
                        SELECT u.id, u.display_name, u.email, t.code, t.checked_in_at
                        FROM event_ticket t JOIN app_user u ON u.id = t.user_id
                        WHERE t.event_id = ?
                        ORDER BY t.checked_in_at DESC NULLS LAST, lower(u.display_name)""")
                .param(eventId)
                .query((rs, i) -> new AttendeeView(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("email"), rs.getString("code"), instant(rs.getTimestamp("checked_in_at"))))
                .list();
        int checkedIn = (int) attendees.stream().filter(a -> a.checkedInAt() != null).count();
        return new CheckinSummary(attendees.size(), checkedIn, attendees);
    }

    // ---- internals ----

    private CheckinResult markCheckedIn(UUID eventId, UUID attendee, UUID organiser) {
        Map<String, Object> row = jdbc.sql("""
                        SELECT u.display_name, u.email, t.checked_in_at
                        FROM event_ticket t JOIN app_user u ON u.id = t.user_id
                        WHERE t.event_id = ? AND t.user_id = ? FOR UPDATE OF t""")
                .params(eventId, attendee).query().singleRow();
        Instant already = instant(row.get("checked_in_at"));
        Instant at = already;
        if (already == null) {
            at = Instant.now();
            jdbc.sql("UPDATE event_ticket SET checked_in_at = ?, checked_in_by = ? WHERE event_id = ? AND user_id = ?")
                    .params(Timestamp.from(at), organiser, eventId, attendee).update();
        }
        return new CheckinResult(attendee, (String) row.get("display_name"), (String) row.get("email"), at, already != null);
    }

    private void ensureTicket(UUID eventId, UUID userId) {
        // Retry on the (very unlikely) chance two tickets get the same random code.
        for (int attempt = 0; attempt < 5; attempt++) {
            int inserted = jdbc.sql("""
                            INSERT INTO event_ticket (event_id, user_id, code) VALUES (?, ?, ?)
                            ON CONFLICT DO NOTHING""")
                    .params(eventId, userId, newCode()).update();
            boolean exists = jdbc.sql("SELECT count(*) FROM event_ticket WHERE event_id = ? AND user_id = ?")
                    .params(eventId, userId).query(Integer.class).single() > 0;
            if (inserted > 0 || exists) {
                return;
            }
        }
        throw new IllegalStateException("Could not create a ticket code");
    }

    /** Accepts the raw QR text ("ITLSUMMIT:ABCD2345") or a typed code ("abcd 2345", "ABCD-2345"). */
    static String normalise(String input) {
        String s = input.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith(QR_PREFIX)) {
            s = s.substring(QR_PREFIX.length());
        }
        return s.replaceAll("[\\s-]", "");
    }

    private static String newCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        return value instanceof Instant i ? i : null;
    }
}
