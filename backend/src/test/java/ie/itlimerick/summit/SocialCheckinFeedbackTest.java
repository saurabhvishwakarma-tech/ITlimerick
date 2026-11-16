package ie.itlimerick.summit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Google sign-in (against a fake Google running inside the test), QR check-in and session feedback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialCheckinFeedbackTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String SLUG = "summit-test";

    /** code -> the profile our fake Google returns for it. */
    private static final Map<String, String> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_TOKEN_REQUEST = new ConcurrentHashMap<>();
    private static final HttpServer FAKE_GOOGLE = startFakeGoogle();

    @DynamicPropertySource
    static void oauth(DynamicPropertyRegistry r) {
        String base = "http://localhost:" + FAKE_GOOGLE.getAddress().getPort();
        r.add("app.public-base-url", () -> "https://summit.example");
        r.add("app.oauth.google.client-id", () -> "test-client");
        r.add("app.oauth.google.client-secret", () -> "test-secret");
        r.add("app.oauth.google.authorization-uri", () -> base + "/authorize");
        r.add("app.oauth.google.token-uri", () -> base + "/token");
        r.add("app.oauth.google.user-info-uri", () -> base + "/userinfo");
    }

    @AfterAll
    static void stop() {
        FAKE_GOOGLE.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie admin;
    private String eventId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE event, app_user, oauth_login_state CASCADE");
        admin = register("admin@test.ie", "Admin");
        String event = call(post("/api/v1/admin/events"), admin,
                "{\"slug\":\"" + SLUG + "\",\"name\":\"Summit\",\"status\":\"PUBLISHED\",\"timezone\":\"Europe/Dublin\"}", 201);
        eventId = JsonPath.read(event, "$.id");
    }

    // ---------------- Google / LinkedIn sign-in ----------------

    @Test
    void onlyConfiguredProvidersAreOffered() throws Exception {
        String body = call(get("/api/v1/auth/providers"), null, null, 200);
        assertThat(JsonPath.<List<String>>read(body, "$.providers")).containsExactly("google");
        MvcResult r = mvc.perform(get("/api/v1/auth/oauth/linkedin/start")).andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).isEqualTo("/login?oauthError=unavailable");
    }

    @Test
    void googleSignInCreatesAccountAndLogsIn() throws Exception {
        PROFILES.put("code-1", profile("g-111", "new.person@gmail.com", true, "New Person"));
        MvcResult start = mvc.perform(get("/api/v1/auth/oauth/google/start").param("returnTo", "/e/" + SLUG + "/teams"))
                .andReturn();
        assertThat(start.getResponse().getStatus()).isEqualTo(302);
        URI to = URI.create(start.getResponse().getRedirectedUrl());
        Map<String, String> q = query(to);
        assertThat(to.getPath()).isEqualTo("/authorize");
        assertThat(q).containsEntry("client_id", "test-client")
                .containsEntry("redirect_uri", "https://summit.example/api/v1/auth/oauth/google/callback")
                .containsEntry("code_challenge_method", "S256")
                .containsKey("code_challenge");
        Cookie state = start.getResponse().getCookie("SUMMIT_OAUTH_STATE");
        assertThat(state.getValue()).isEqualTo(q.get("state"));
        assertThat(state.isHttpOnly()).isTrue();

        MvcResult back = mvc.perform(get("/api/v1/auth/oauth/google/callback")
                .param("code", "code-1").param("state", q.get("state")).cookie(state)).andReturn();
        assertThat(back.getResponse().getRedirectedUrl()).isEqualTo("/e/" + SLUG + "/teams");
        Cookie session = back.getResponse().getCookie("SUMMIT_SESSION");
        assertThat(session).isNotNull();
        // Both cookies must really be on the response (a browser only sees the Set-Cookie headers).
        assertThat(back.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(h -> h.startsWith("SUMMIT_SESSION=") && !h.startsWith("SUMMIT_SESSION=;"))
                .anyMatch(h -> h.startsWith("SUMMIT_OAUTH_STATE=;") || h.startsWith("SUMMIT_OAUTH_STATE=\"\""));
        assertThat(LAST_TOKEN_REQUEST.get("body")).contains("code_verifier=").contains("client_secret=test-secret");

        String me = call(get("/api/v1/me"), session, null, 200);
        assertThat(JsonPath.<String>read(me, "$.email")).isEqualTo("new.person@gmail.com");
        assertThat(JsonPath.<String>read(me, "$.displayName")).isEqualTo("New Person");
        assertThat(JsonPath.<Boolean>read(me, "$.hasPassword")).isFalse();
        assertThat(JsonPath.<List<String>>read(me, "$.connectedAccounts")).containsExactly("google");

        // The same state can't be used twice.
        MvcResult replay = mvc.perform(get("/api/v1/auth/oauth/google/callback")
                .param("code", "code-1").param("state", q.get("state")).cookie(state)).andReturn();
        assertThat(replay.getResponse().getRedirectedUrl()).isEqualTo("/login?oauthError=expired");

        // Signing in again with the same Google account finds the same person.
        Cookie again = googleLogin("code-2", profile("g-111", "renamed@gmail.com", true, "Renamed"));
        assertThat(JsonPath.<String>read(call(get("/api/v1/me"), again, null, 200), "$.email")).isEqualTo("new.person@gmail.com");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(2);

        // No password yet: they can set one without a "current password", and can't remove their only login.
        call(delete("/api/v1/me/connected-accounts/google"), again, null, 400);
        call(put("/api/v1/me/password"), again, "{\"newPassword\":\"a-brand-new-password\"}", 204);
    }

    @Test
    void googleSignInJoinsExistingAccountWithSameVerifiedEmail() throws Exception {
        Cookie password = register("ana@example.ie", "Ana");
        String userId = JsonPath.read(call(get("/api/v1/me"), password, null, 200), "$.id");
        Cookie viaGoogle = googleLogin("code-3", profile("g-ana", "Ana@Example.ie", true, "Ana G"));
        assertThat(JsonPath.<String>read(call(get("/api/v1/me"), viaGoogle, null, 200), "$.id")).isEqualTo(userId);
    }

    @Test
    void refusesUnverifiedEmailAndForgedState() throws Exception {
        PROFILES.put("code-4", profile("g-x", "x@example.ie", false, "X"));
        MvcResult start = mvc.perform(get("/api/v1/auth/oauth/google/start")).andReturn();
        String state = query(URI.create(start.getResponse().getRedirectedUrl())).get("state");
        Cookie cookie = start.getResponse().getCookie("SUMMIT_OAUTH_STATE");

        MvcResult forged = mvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "code-4")
                .param("state", state).cookie(new Cookie("SUMMIT_OAUTH_STATE", "someone-elses"))).andReturn();
        assertThat(forged.getResponse().getRedirectedUrl()).isEqualTo("/login?oauthError=expired");

        MvcResult unverified = mvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "code-4")
                .param("state", state).cookie(cookie)).andReturn();
        assertThat(unverified.getResponse().getRedirectedUrl()).isEqualTo("/login?oauthError=no-email");

        MvcResult cancelled = mvc.perform(get("/api/v1/auth/oauth/google/callback").param("error", "access_denied"))
                .andReturn();
        assertThat(cancelled.getResponse().getRedirectedUrl()).isEqualTo("/login?oauthError=cancelled");
    }

    @Test
    void returnToCannotPointToAnotherSite() throws Exception {
        Cookie c = googleLogin("code-5", profile("g-5", "five@example.ie", true, "Five"), "https://evil.example/");
        assertThat(c).isNotNull();
        MvcResult start = mvc.perform(get("/api/v1/auth/oauth/google/start").param("returnTo", "//evil.example")).andReturn();
        String state = query(URI.create(start.getResponse().getRedirectedUrl())).get("state");
        PROFILES.put("code-6", profile("g-5", "five@example.ie", true, "Five"));
        MvcResult back = mvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", "code-6").param("state", state)
                .cookie(start.getResponse().getCookie("SUMMIT_OAUTH_STATE"))).andReturn();
        assertThat(back.getResponse().getRedirectedUrl()).isEqualTo("/");
    }

    // ---------------- QR check-in ----------------

    @Test
    void ticketScanAndManualCheckin() throws Exception {
        Cookie ana = register("ana@example.ie", "Ana");
        register("ben@example.ie", "Ben");

        String ticket = call(get("/api/v1/me/ticket").param("event", SLUG), ana, null, 200);
        String code = JsonPath.read(ticket, "$.code");
        assertThat(code).hasSize(8);
        assertThat(JsonPath.<String>read(ticket, "$.qrText")).isEqualTo("ITLSUMMIT:" + code);
        // Asking again returns the same ticket.
        assertThat(JsonPath.<String>read(call(get("/api/v1/me/ticket").param("event", SLUG), ana, null, 200), "$.code"))
                .isEqualTo(code);

        String checkins = "/api/v1/admin/events/" + eventId + "/checkins";
        call(post(checkins), ana, "{\"code\":\"" + code + "\"}", 403);
        call(post(checkins), admin, "{\"code\":\"NOPE1234\"}", 404);

        String first = call(post(checkins), admin, "{\"code\":\"ITLSUMMIT:" + code + "\"}", 200);
        assertThat(JsonPath.<String>read(first, "$.name")).isEqualTo("Ana");
        assertThat(JsonPath.<Boolean>read(first, "$.alreadyCheckedIn")).isFalse();
        String typed = code.substring(0, 4).toLowerCase() + " " + code.substring(4);
        assertThat(JsonPath.<Boolean>read(call(post(checkins), admin, "{\"code\":\"" + typed + "\"}", 200),
                "$.alreadyCheckedIn")).isTrue();

        call(post(checkins + "/by-email"), admin, "{\"email\":\"BEN@example.ie\"}", 200);
        call(post(checkins + "/by-email"), admin, "{\"email\":\"nobody@example.ie\"}", 404);

        String list = call(get(checkins), admin, null, 200);
        assertThat(JsonPath.<Integer>read(list, "$.tickets")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(list, "$.checkedIn")).isEqualTo(2);

        String anaId = JsonPath.read(first, "$.userId");
        call(delete(checkins + "/" + anaId), admin, null, 204);
        assertThat(JsonPath.<Integer>read(call(get(checkins), admin, null, 200), "$.checkedIn")).isEqualTo(1);
        assertThat(JsonPath.<Object>read(call(get("/api/v1/me/ticket").param("event", SLUG), ana, null, 200),
                "$.checkedInAt")).isNull();
    }

    // ---------------- Session feedback ----------------

    @Test
    void feedbackOnlyAfterStartAndAnonymousSummary() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String done = JsonPath.read(call(post("/api/v1/admin/events/" + eventId + "/sessions"), admin,
                "{\"title\":\"Keynote\",\"type\":\"KEYNOTE\",\"startsAt\":\"" + past + "\"}", 201), "$.id");
        String later = JsonPath.read(call(post("/api/v1/admin/events/" + eventId + "/sessions"), admin,
                "{\"title\":\"Workshop\",\"type\":\"WORKSHOP\",\"startsAt\":\"" + future + "\"}", 201), "$.id");

        Cookie ana = register("ana@example.ie", "Ana");
        Cookie ben = register("ben@example.ie", "Ben");
        call(put("/api/v1/me/sessions/" + later + "/feedback"), ana, "{\"rating\":5}", 400);
        call(put("/api/v1/me/sessions/" + done + "/feedback"), ana, "{\"rating\":6}", 400);
        call(put("/api/v1/me/sessions/" + done + "/feedback"), ana, "{\"rating\":3,\"comment\":\"Too fast\"}", 200);
        call(put("/api/v1/me/sessions/" + done + "/feedback"), ana, "{\"rating\":4,\"comment\":\"Good after all\"}", 200);
        call(put("/api/v1/me/sessions/" + done + "/feedback"), ben, "{\"rating\":5}", 200);

        String mine = call(get("/api/v1/me/feedback").param("event", SLUG), ana, null, 200);
        assertThat(JsonPath.<Integer>read(mine, "$[0].rating")).isEqualTo(4);

        call(get("/api/v1/admin/events/" + eventId + "/feedback"), ana, null, 403);
        String summary = call(get("/api/v1/admin/events/" + eventId + "/feedback"), admin, null, 200);
        assertThat(JsonPath.<Integer>read(summary, "$[0].responses")).isEqualTo(2);
        assertThat(JsonPath.<Double>read(summary, "$[0].averageRating")).isEqualTo(4.5);
        assertThat(JsonPath.<List<String>>read(summary, "$[0].comments[*].comment")).containsExactly("Good after all");
        assertThat(summary).doesNotContain("Ana").doesNotContain("ana@example.ie");

        String export = call(get("/api/v1/me/export"), ana, null, 200);
        assertThat(JsonPath.<List<Object>>read(export, "$.sessionFeedback")).hasSize(1);

        call(delete("/api/v1/me/sessions/" + done + "/feedback"), ana, null, 204);
        assertThat(JsonPath.<Integer>read(call(get("/api/v1/admin/events/" + eventId + "/feedback"), admin, null, 200),
                "$[0].responses")).isEqualTo(1);
    }

    // ---------------- helpers ----------------

    private Cookie googleLogin(String code, String profileJson) throws Exception {
        return googleLogin(code, profileJson, null);
    }

    private Cookie googleLogin(String code, String profileJson, String returnTo) throws Exception {
        PROFILES.put(code, profileJson);
        MockHttpServletRequestBuilder startReq = get("/api/v1/auth/oauth/google/start");
        if (returnTo != null) {
            startReq.param("returnTo", returnTo);
        }
        MvcResult start = mvc.perform(startReq).andReturn();
        String state = query(URI.create(start.getResponse().getRedirectedUrl())).get("state");
        MvcResult back = mvc.perform(get("/api/v1/auth/oauth/google/callback").param("code", code).param("state", state)
                .cookie(start.getResponse().getCookie("SUMMIT_OAUTH_STATE"))).andReturn();
        if (returnTo != null) {
            assertThat(back.getResponse().getRedirectedUrl()).isEqualTo("/");
        }
        return back.getResponse().getCookie("SUMMIT_SESSION");
    }

    private static String profile(String sub, String email, boolean verified, String name) {
        return "{\"sub\":\"%s\",\"email\":\"%s\",\"email_verified\":%s,\"name\":\"%s\"}".formatted(sub, email, verified, name);
    }

    private Cookie register(String email, String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"%s\",\"acceptPrivacy\":true}"
                                .formatted(email, PASSWORD, name)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return r.getResponse().getCookie("SUMMIT_SESSION");
    }

    private String call(MockHttpServletRequestBuilder req, Cookie cookie, String body, int expected) throws Exception {
        req.with(csrf());
        if (cookie != null) {
            req.cookie(cookie);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcResult r = mvc.perform(req).andReturn();
        assertThat(r.getResponse().getStatus())
                .as("%s %s -> %s", r.getRequest().getMethod(), r.getRequest().getRequestURI(), r.getResponse().getContentAsString())
                .isEqualTo(expected);
        return r.getResponse().getContentAsString();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> out = new java.util.HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return out;
    }

    private static HttpServer startFakeGoogle() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/token", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                LAST_TOKEN_REQUEST.put("body", body);
                String code = URLDecoder.decode(body.replaceAll(".*(?:^|&)code=([^&]*).*", "$1"), StandardCharsets.UTF_8);
                if (!PROFILES.containsKey(code)) {
                    send(ex, 400, "{\"error\":\"invalid_grant\"}");
                } else {
                    send(ex, 200, "{\"access_token\":\"tok-" + code + "\",\"token_type\":\"Bearer\"}");
                }
            });
            server.createContext("/userinfo", ex -> {
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                String code = auth == null ? "" : auth.replace("Bearer tok-", "");
                String profile = PROFILES.get(code);
                send(ex, profile == null ? 401 : 200, profile == null ? "{}" : profile);
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
