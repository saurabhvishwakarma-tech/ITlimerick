package ie.itlimerick.summit.feedback;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.security.CurrentUser;

/**
 * Session feedback: 1–5 stars and an optional comment, once a session has started.
 * People can change or remove their feedback. Organisers see totals and comments without names.
 */
@RestController
public class FeedbackController {

    public record FeedbackRequest(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {
    }

    public record MyFeedbackView(UUID sessionId, int rating, String comment) {
    }

    public record CommentView(int rating, String comment, Instant at) {
    }

    public record SessionFeedbackView(UUID sessionId, String title, Instant startsAt, int responses, Double averageRating,
                                      int[] ratingCounts, List<CommentView> comments) {
    }

    private final JdbcClient jdbc;
    private final EventService events;

    public FeedbackController(JdbcClient jdbc, EventService events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @GetMapping("/api/v1/me/feedback")
    @Transactional(readOnly = true)
    public List<MyFeedbackView> mine(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user) {
        Event event = events.requirePublished(slug);
        return jdbc.sql("""
                        SELECT f.session_id, f.rating, f.comment FROM session_feedback f
                        JOIN agenda_session s ON s.id = f.session_id
                        WHERE s.event_id = ? AND f.user_id = ?""")
                .params(event.getId(), user.id())
                .query((rs, i) -> new MyFeedbackView(rs.getObject("session_id", UUID.class), rs.getInt("rating"),
                        rs.getString("comment")))
                .list();
    }

    @PutMapping("/api/v1/me/sessions/{sessionId}/feedback")
    @Transactional
    public MyFeedbackView give(@PathVariable UUID sessionId, @Valid @RequestBody FeedbackRequest req,
                               @AuthenticationPrincipal CurrentUser user) {
        Map<String, Object> session = jdbc.sql("""
                        SELECT s.starts_at, s.status, s.type, e.status AS event_status FROM agenda_session s
                        JOIN event e ON e.id = s.event_id WHERE s.id = ?""")
                .param(sessionId).query().listOfRows().stream().findFirst()
                .filter(r -> "PUBLISHED".equals(r.get("event_status")))
                .orElseThrow(() -> ApiException.notFound("Session"));
        if ("CANCELLED".equals(session.get("status")) || "BREAK".equals(session.get("type"))) {
            throw ApiException.badRequest("This session can't be rated.");
        }
        Instant startsAt = toInstant(session.get("starts_at"));
        if (startsAt != null && startsAt.isAfter(Instant.now())) {
            throw ApiException.badRequest("You can rate this session once it has started.");
        }
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().strip();
        jdbc.sql("""
                        INSERT INTO session_feedback (session_id, user_id, rating, comment) VALUES (?, ?, ?, ?)
                        ON CONFLICT (session_id, user_id)
                        DO UPDATE SET rating = excluded.rating, comment = excluded.comment, updated_at = now()""")
                .params(sessionId, user.id(), req.rating(), comment).update();
        return new MyFeedbackView(sessionId, req.rating(), comment);
    }

    @DeleteMapping("/api/v1/me/sessions/{sessionId}/feedback")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable UUID sessionId, @AuthenticationPrincipal CurrentUser user) {
        jdbc.sql("DELETE FROM session_feedback WHERE session_id = ? AND user_id = ?").params(sessionId, user.id()).update();
        return ResponseEntity.noContent().build();
    }

    /** Totals per session plus comments. Names are deliberately left out. */
    @GetMapping("/api/v1/admin/events/{eventId}/feedback")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional(readOnly = true)
    public List<SessionFeedbackView> summary(@PathVariable UUID eventId) {
        Map<UUID, SessionFeedbackView> bySession = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT s.id, s.title, s.starts_at, f.rating, f.comment, f.updated_at
                        FROM agenda_session s JOIN session_feedback f ON f.session_id = s.id
                        WHERE s.event_id = ?
                        ORDER BY s.starts_at, s.title, f.updated_at DESC""")
                .param(eventId)
                .query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    SessionFeedbackView v = bySession.computeIfAbsent(id, k -> {
                        try {
                            return new SessionFeedbackView(id, rs.getString("title"), toInstant(rs.getTimestamp("starts_at")),
                                    0, null, new int[5], new ArrayList<>());
                        } catch (java.sql.SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    int rating = rs.getInt("rating");
                    v.ratingCounts()[rating - 1]++;
                    String comment = rs.getString("comment");
                    if (comment != null) {
                        v.comments().add(new CommentView(rating, comment, toInstant(rs.getTimestamp("updated_at"))));
                    }
                });
        return bySession.values().stream().map(v -> {
            int total = 0;
            int sum = 0;
            for (int i = 0; i < 5; i++) {
                total += v.ratingCounts()[i];
                sum += v.ratingCounts()[i] * (i + 1);
            }
            Double avg = total == 0 ? null : Math.round(sum * 10.0 / total) / 10.0;
            return new SessionFeedbackView(v.sessionId(), v.title(), v.startsAt(), total, avg, v.ratingCounts(), v.comments());
        }).toList();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime o) {
            return o.toInstant();
        }
        return value instanceof Instant i ? i : null;
    }
}
