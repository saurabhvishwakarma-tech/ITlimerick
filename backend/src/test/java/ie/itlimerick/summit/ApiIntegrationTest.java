package ie.itlimerick.summit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ie.itlimerick.summit.common.Tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the REST API against a real PostgreSQL database (see application-test.yml).
 * Each test starts from empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("""
                TRUNCATE password_reset_token, auth_session, consent_record, bookmark, announcement,
                         session_speaker, agenda_session, speaker, sponsor, room, event_membership, event, app_user CASCADE
                """);
    }

    // ---------- helpers ----------

    private Cookie register(String email, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"%s","acceptPrivacy":true,"marketingOptIn":false}
                                """.formatted(email, PASSWORD, name)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SUMMIT_SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    private String json(MockHttpServletRequestBuilder req, Cookie cookie, String body, int expectedStatus) throws Exception {
        req.with(csrf()).cookie(cookie);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        assertThat(r.getResponse().getStatus())
                .as("status for %s: %s", r.getRequest().getRequestURI(), r.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        return r.getResponse().getContentAsString();
    }

    /** Creates a published event with one room, speaker, session and announcement. Returns the event id. */
    private String seedEvent(Cookie admin, String slug, String status) throws Exception {
        String event = json(post("/api/v1/admin/events"), admin, """
                {"slug":"%s","name":"IT Limerick Summit","venueName":"TUS","startsAt":"2027-02-18T09:00:00Z",
                 "endsAt":"2027-02-18T17:00:00Z","timezone":"Europe/Dublin","status":"%s"}
                """.formatted(slug, status), 201);
        String eventId = JsonPath.read(event, "$.id");
        String room = json(post("/api/v1/admin/events/" + eventId + "/rooms"), admin,
                "{\"name\":\"Main Hall\",\"sortOrder\":1}", 201);
        String speaker = json(post("/api/v1/admin/events/" + eventId + "/speakers"), admin, """
                {"name":"Ada Lovelace","company":"Analytical Engines","published":true,"sortOrder":0,
                 "photoUrl":"https://example.com/ada.jpg"}
                """, 201);
        String hidden = json(post("/api/v1/admin/events/" + eventId + "/speakers"), admin, """
                {"name":"Hidden Person","published":false,"sortOrder":1}
                """, 201);
        json(post("/api/v1/admin/events/" + eventId + "/sessions"), admin, """
                {"title":"Opening keynote","type":"KEYNOTE","startsAt":"2027-02-18T09:30:00Z",
                 "endsAt":"2027-02-18T10:00:00Z","roomId":"%s","speakerIds":["%s","%s"]}
                """.formatted(JsonPath.read(room, "$.id"), JsonPath.read(speaker, "$.id"), JsonPath.read(hidden, "$.id")), 201);
        json(post("/api/v1/admin/events/" + eventId + "/sponsors"), admin, """
                {"name":"Acme","tier":"Gold","tierRank":1,"sortOrder":0,"websiteUrl":"https://acme.example"}
                """, 201);
        json(post("/api/v1/admin/events/" + eventId + "/announcements"), admin,
                "{\"title\":\"Doors open at 8:30\",\"priority\":\"NORMAL\",\"pinned\":false}", 201);
        return eventId;
    }

    // ---------- tests ----------

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void publicContentIsVisibleOnlyForPublishedEvents() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        seedEvent(admin, "summit-2027", "PUBLISHED");
        seedEvent(admin, "secret-draft", "DRAFT");

        mvc.perform(get("/api/v1/events")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/events/secret-draft")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events/secret-draft/sessions")).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/events/summit-2027/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Opening keynote"))
                .andExpect(jsonPath("$[0].room.name").value("Main Hall"))
                // unpublished speakers never appear publicly
                .andExpect(jsonPath("$[0].speakers", hasSize(1)))
                .andExpect(jsonPath("$[0].speakers[0].name").value("Ada Lovelace"));

        mvc.perform(get("/api/v1/events/summit-2027/speakers")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/events/summit-2027/sponsors")).andExpect(jsonPath("$[0].tier").value("Gold"));
        mvc.perform(get("/api/v1/events/summit-2027/announcements"))
                .andExpect(jsonPath("$[0].title").value("Doors open at 8:30"))
                .andExpect(header().exists("ETag"));
    }

    @Test
    void etagLetsPhonesSkipUnchangedAnnouncements() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        seedEvent(admin, "summit-2027", "PUBLISHED");
        String etag = mvc.perform(get("/api/v1/events/summit-2027/announcements"))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/events/summit-2027/announcements").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.ie\",\"password\":\"%s\",\"displayName\":\"A\",\"acceptPrivacy\":true}".formatted(PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerLoginLogoutAndSession() throws Exception {
        Cookie cookie = register("Sam@Example.ie", "Sam");

        mvc.perform(get("/api/v1/auth/session").cookie(cookie))
                .andExpect(jsonPath("$.user.email").value("sam@example.ie"))
                .andExpect(jsonPath("$.user.platformAdmin").value(false));
        mvc.perform(get("/api/v1/auth/session")).andExpect(jsonPath("$.user").value(nullValue()));

        // duplicate email
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sam@example.ie\",\"password\":\"%s\",\"displayName\":\"S\",\"acceptPrivacy\":true}".formatted(PASSWORD)))
                .andExpect(status().isConflict());
        // privacy notice must be accepted
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.ie\",\"password\":\"%s\",\"displayName\":\"X\",\"acceptPrivacy\":false}".formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.acceptPrivacy").exists());

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sam@example.ie\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"SAM@example.ie\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        Cookie second = login.getResponse().getCookie("SUMMIT_SESSION");

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(second)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").cookie(second)).andExpect(status().isUnauthorized());
        // the first session is still valid
        mvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void repeatedWrongPasswordsAreBlocked() throws Exception {
        register("target@example.ie", "Target");
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"target@example.ie\",\"password\":\"guess-number-%d\"}".formatted(i)));
        }
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"target@example.ie\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void adminEndpointsAreProtected() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        String eventId = seedEvent(admin, "summit-2027", "PUBLISHED");
        Cookie attendee = register("attendee@example.ie", "Attendee");

        mvc.perform(get("/api/v1/admin/events/" + eventId + "/sessions")).andExpect(status().isUnauthorized());
        json(get("/api/v1/admin/events/" + eventId + "/sessions"), attendee, null, 403);
        json(post("/api/v1/admin/events"), attendee,
                "{\"slug\":\"x-event\",\"name\":\"X\",\"status\":\"DRAFT\"}", 403);

        // make the attendee an organiser of this event: now they can manage it, but not create events
        json(post("/api/v1/admin/events/" + eventId + "/members"), admin,
                "{\"email\":\"attendee@example.ie\",\"role\":\"ORGANISER\"}", 201);
        json(get("/api/v1/admin/events/" + eventId + "/sessions"), attendee, null, 200);
        json(post("/api/v1/admin/events"), attendee,
                "{\"slug\":\"x-event\",\"name\":\"X\",\"status\":\"DRAFT\"}", 403);
        String mine = json(get("/api/v1/admin/events"), attendee, null, 200);
        assertThat((List<?>) JsonPath.read(mine, "$")).hasSize(1);
    }

    @Test
    void organisersCannotTouchOtherEventsData() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        String eventA = seedEvent(admin, "event-a", "PUBLISHED");
        String eventB = seedEvent(admin, "event-b", "PUBLISHED");
        String sessionsOfB = json(get("/api/v1/admin/events/" + eventB + "/sessions"), admin, null, 200);
        String sessionB = JsonPath.read(sessionsOfB, "$[0].id");
        // editing event B's session through event A's URL is refused
        json(delete("/api/v1/admin/events/" + eventA + "/sessions/" + sessionB), admin, null, 404);
    }

    @Test
    void validationRejectsBadInput() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        String eventId = seedEvent(admin, "summit-2027", "PUBLISHED");
        json(post("/api/v1/admin/events/" + eventId + "/sessions"), admin, """
                {"title":"Backwards","type":"TALK","startsAt":"2027-02-18T10:00:00Z","endsAt":"2027-02-18T09:00:00Z"}
                """, 400);
        json(post("/api/v1/admin/events/" + eventId + "/speakers"), admin,
                "{\"name\":\"Bad link\",\"photoUrl\":\"javascript:alert(1)\",\"published\":true}", 400);
        json(post("/api/v1/admin/events/" + eventId + "/sessions"), admin, """
                {"title":"Wrong speaker","type":"TALK","startsAt":"2027-02-18T10:00:00Z","speakerIds":["%s"]}
                """.formatted(UUID.randomUUID()), 400);
    }

    @Test
    void myScheduleBookmarks() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        seedEvent(admin, "summit-2027", "PUBLISHED");
        Cookie attendee = register("attendee@example.ie", "Attendee");
        String sessions = mvc.perform(get("/api/v1/events/summit-2027/sessions")).andReturn().getResponse().getContentAsString();
        String sessionId = JsonPath.read(sessions, "$[0].id");

        mvc.perform(get("/api/v1/me/schedule?event=summit-2027")).andExpect(status().isUnauthorized());
        json(put("/api/v1/me/schedule/" + sessionId), attendee, null, 204);
        json(put("/api/v1/me/schedule/" + sessionId), attendee, null, 204); // idempotent
        String saved = json(get("/api/v1/me/schedule?event=summit-2027"), attendee, null, 200);
        assertThat((List<String>) JsonPath.read(saved, "$")).containsExactly(sessionId);
        json(delete("/api/v1/me/schedule/" + sessionId), attendee, null, 204);
        saved = json(get("/api/v1/me/schedule?event=summit-2027"), attendee, null, 200);
        assertThat((List<?>) JsonPath.read(saved, "$")).isEmpty();
    }

    @Test
    void gdprExportAndDeleteAccount() throws Exception {
        Cookie admin = register("admin@test.ie", "Admin");
        seedEvent(admin, "summit-2027", "PUBLISHED");
        Cookie attendee = register("leaver@example.ie", "Leaver");
        String sessions = mvc.perform(get("/api/v1/events/summit-2027/sessions")).andReturn().getResponse().getContentAsString();
        json(put("/api/v1/me/schedule/" + JsonPath.read(sessions, "$[0].id")), attendee, null, 204);
        json(put("/api/v1/me/consents"), attendee, "{\"marketing\":true}", 200);

        String export = json(get("/api/v1/me/export"), attendee, null, 200);
        assertThat((String) JsonPath.read(export, "$.account.email")).isEqualTo("leaver@example.ie");
        assertThat((List<?>) JsonPath.read(export, "$.savedSessions")).hasSize(1);
        assertThat((List<?>) JsonPath.read(export, "$.consents")).hasSize(3);

        json(delete("/api/v1/me"), attendee, null, 204);
        Integer remaining = jdbc.queryForObject(
                "select count(*) from app_user where email = 'leaver@example.ie'", Integer.class);
        assertThat(remaining).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bookmark", Integer.class)).isZero();
        mvc.perform(get("/api/v1/me").cookie(attendee)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetWithValidTokenOnlyWorksOnce() throws Exception {
        Cookie cookie = register("forgetful@example.ie", "Forgetful");
        mvc.perform(post("/api/v1/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.ie\"}")).andExpect(status().isNoContent());

        UUID userId = jdbc.queryForObject("select id from app_user where email = 'forgetful@example.ie'", UUID.class);
        String token = Tokens.newToken();
        jdbc.update("insert into password_reset_token (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId, Tokens.sha256(token), java.sql.Timestamp.from(Instant.now().plusSeconds(600)));

        String body = "{\"token\":\"%s\",\"newPassword\":\"a-brand-new-password\"}".formatted(token);
        mvc.perform(post("/api/v1/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        // old sessions were logged out
        mvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgetful@example.ie\",\"password\":\"a-brand-new-password\"}"))
                .andExpect(status().isOk());
    }
}
