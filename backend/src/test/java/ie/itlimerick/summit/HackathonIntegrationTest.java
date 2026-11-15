package ie.itlimerick.summit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Hackathon teams, the team sorter, voting and chat, end to end against PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HackathonIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String SLUG = "hack-2027";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie admin;
    private String eventId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE event, app_user CASCADE");
        admin = register("admin@test.ie", "Admin");
        String event = call(post("/api/v1/admin/events"), admin, eventBody(true, 3, null, null, false, true), 201);
        eventId = JsonPath.read(event, "$.id");
    }

    // ---------- helpers ----------

    private String eventBody(boolean hackathon, int teamSize, Instant opens, Instant closes, boolean resultsPublic,
                             boolean chat) {
        return """
                {"slug":"%s","name":"Hack","status":"PUBLISHED","timezone":"Europe/Dublin","hackathonEnabled":%s,
                 "teamMaxSize":%d,"votingOpensAt":%s,"votingClosesAt":%s,"votingResultsPublic":%s,"chatEnabled":%s}
                """.formatted(SLUG, hackathon, teamSize, opens == null ? "null" : "\"" + opens + "\"",
                closes == null ? "null" : "\"" + closes + "\"", resultsPublic, chat);
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

    private void setVoting(Instant opens, Instant closes, boolean resultsPublic) throws Exception {
        call(put("/api/v1/admin/events/" + eventId), admin, eventBody(true, 3, opens, closes, resultsPublic, true), 200);
    }

    // ---------- tests ----------

    @Test
    void teamsAreHiddenWhenHackathonIsOff() throws Exception {
        call(put("/api/v1/admin/events/" + eventId), admin, eventBody(false, 3, null, null, false, false), 200);
        call(get("/api/v1/events/" + SLUG + "/teams"), null, null, 404);
        call(get("/api/v1/events/" + SLUG + "/voting"), null, null, 404);
        call(get("/api/v1/events/" + SLUG + "/chat"), admin, null, 404);
    }

    @Test
    void createJoinFullAndLeaveTeam() throws Exception {
        Cookie ana = register("ana@example.ie", "Ana");
        Cookie ben = register("ben@example.ie", "Ben");
        Cookie cara = register("cara@example.ie", "Cara");
        Cookie dan = register("dan@example.ie", "Dan");

        String mine = call(post("/api/v1/me/team?event=" + SLUG), ana,
                "{\"name\":\"Shannon Coders\",\"projectName\":\"FloodWatch\",\"sector\":\"Climate\"}", 200);
        String code = JsonPath.read(mine, "$.joinCode");
        assertThat((String) JsonPath.read(mine, "$.myRole")).isEqualTo("LEAD");

        call(post("/api/v1/me/team/join"), ben, "{\"code\":\"" + code.toLowerCase() + "\"}", 200);
        call(post("/api/v1/me/team/join"), cara, "{\"code\":\"" + code + "\"}", 200);
        // team size limit is 3
        call(post("/api/v1/me/team/join"), dan, "{\"code\":\"" + code + "\"}", 409);
        // can't be in two teams
        call(post("/api/v1/me/team?event=" + SLUG), ben, "{\"name\":\"Second team\"}", 409);
        call(post("/api/v1/me/team/join"), dan, "{\"code\":\"NOPE99\"}", 400);

        // member names are shown to logged-in people only
        String publicList = call(get("/api/v1/events/" + SLUG + "/teams"), null, null, 200);
        assertThat((Integer) JsonPath.read(publicList, "$[0].memberCount")).isEqualTo(3);
        assertThat((List<?>) JsonPath.read(publicList, "$[0].memberNames")).isEmpty();
        String loggedIn = call(get("/api/v1/events/" + SLUG + "/teams"), dan, null, 200);
        assertThat((List<String>) JsonPath.read(loggedIn, "$[0].memberNames")).containsExactly("Ana", "Ben", "Cara");

        // when the lead leaves, the next longest-standing member takes over
        call(post("/api/v1/me/team/leave?event=" + SLUG), ana, null, 200);
        String benView = call(get("/api/v1/me/hackathon?event=" + SLUG), ben, null, 200);
        assertThat((String) JsonPath.read(benView, "$.myRole")).isEqualTo("LEAD");

        // members can edit their project; the join code isn't exposed publicly
        call(put("/api/v1/me/team?event=" + SLUG), cara,
                "{\"name\":\"Shannon Coders\",\"projectName\":\"FloodWatch 2\",\"projectUrl\":\"https://example.com\"}", 200);
        assertThat(call(get("/api/v1/events/" + SLUG + "/teams"), null, null, 200)).contains("FloodWatch 2").doesNotContain(code);
    }

    @Test
    void teamSorterPlacesEveryoneLookingForATeam() throws Exception {
        // one existing team with one member and room for two more
        Cookie lead = register("lead@example.ie", "Lead");
        call(post("/api/v1/me/team?event=" + SLUG), lead, "{\"name\":\"Existing\"}", 200);

        for (int i = 1; i <= 8; i++) {
            Cookie p = register("p" + i + "@example.ie", "Person " + i);
            call(put("/api/v1/me/hackathon?event=" + SLUG), p, "{\"skills\":\"Java\",\"lookingForTeam\":true}", 200);
        }
        // someone who isn't looking stays out
        Cookie solo = register("solo@example.ie", "Solo");
        call(put("/api/v1/me/hackathon?event=" + SLUG), solo, "{\"lookingForTeam\":false}", 200);

        String result = call(post("/api/v1/admin/events/" + eventId + "/teams/auto-sort"), admin, "{}", 200);
        assertThat((Integer) JsonPath.read(result, "$.assigned")).isEqualTo(8);
        assertThat((Integer) JsonPath.read(result, "$.teamsFilled")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(result, "$.teamsCreated")).isEqualTo(2);

        String teams = call(get("/api/v1/admin/events/" + eventId + "/teams"), admin, null, 200);
        List<Map<String, Object>> list = JsonPath.read(teams, "$");
        assertThat(list).hasSize(3);
        for (Map<String, Object> t : list) {
            List<Map<String, Object>> members = (List<Map<String, Object>>) t.get("members");
            assertThat(members).hasSizeBetween(3, 3);
            assertThat(members.stream().filter(m -> "LEAD".equals(m.get("role")))).hasSize(1);
        }
        String participants = call(get("/api/v1/admin/events/" + eventId + "/participants"), admin, null, 200);
        List<Map<String, Object>> unplaced = JsonPath.read(participants, "$[?(@.teamId == null)]");
        assertThat(unplaced).extracting(m -> m.get("displayName")).containsExactly("Solo");

        // running it again does nothing
        result = call(post("/api/v1/admin/events/" + eventId + "/teams/auto-sort"), admin, "{}", 200);
        assertThat((Integer) JsonPath.read(result, "$.assigned")).isZero();

        // organisers can move someone by hand
        String firstTeam = (String) list.get(0).get("id");
        String soloId = (String) unplaced.get(0).get("userId");
        String moved = call(post("/api/v1/admin/events/" + eventId + "/teams/" + firstTeam + "/members"), admin,
                "{\"userId\":\"" + soloId + "\"}", 200);
        assertThat((List<?>) JsonPath.read(moved, "$.members")).hasSize(4);
    }

    @Test
    void votingRulesAndResults() throws Exception {
        Cookie ana = register("ana@example.ie", "Ana");
        Cookie voter = register("voter@example.ie", "Voter");
        call(post("/api/v1/me/team?event=" + SLUG), ana, "{\"name\":\"Alpha\",\"projectName\":\"A\"}", 200);
        String beta = call(post("/api/v1/admin/events/" + eventId + "/teams"), admin,
                "{\"name\":\"Beta\",\"projectName\":\"B\",\"shortlisted\":false}", 201);
        String betaId = JsonPath.read(beta, "$.id");
        String alphaId = JsonPath.read(call(get("/api/v1/me/hackathon?event=" + SLUG), ana, null, 200), "$.team.id");

        // not scheduled yet
        assertThat((String) JsonPath.read(call(get("/api/v1/events/" + SLUG + "/voting"), null, null, 200), "$.status"))
                .isEqualTo("NOT_SCHEDULED");
        call(put("/api/v1/me/vote?event=" + SLUG), voter, "{\"teamId\":\"" + betaId + "\"}", 409);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        setVoting(now.minusSeconds(3600), now.plusSeconds(3600), true);

        // can't vote for your own team, must be logged in
        call(put("/api/v1/me/vote?event=" + SLUG), ana, "{\"teamId\":\"" + alphaId + "\"}", 400);
        mvc.perform(put("/api/v1/me/vote?event=" + SLUG).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + betaId + "\"}")).andReturn();
        assertThat(jdbc.queryForObject("select count(*) from vote", Integer.class)).isZero();

        call(put("/api/v1/me/vote?event=" + SLUG), ana, "{\"teamId\":\"" + betaId + "\"}", 200);
        call(put("/api/v1/me/vote?event=" + SLUG), voter, "{\"teamId\":\"" + alphaId + "\"}", 200);
        // change of mind: still one vote each
        String view = call(put("/api/v1/me/vote?event=" + SLUG), voter, "{\"teamId\":\"" + betaId + "\"}", 200);
        assertThat((String) JsonPath.read(view, "$.myVoteTeamId")).isEqualTo(betaId);
        assertThat(jdbc.queryForObject("select count(*) from vote", Integer.class)).isEqualTo(2);
        // results hidden while voting is open; organisers see live counts
        assertThat((Object) JsonPath.read(view, "$.results")).isNull();
        String adminView = call(get("/api/v1/admin/events/" + eventId + "/votes"), admin, null, 200);
        assertThat((Integer) JsonPath.read(adminView, "$.results[0].votes")).isEqualTo(2);
        assertThat((String) JsonPath.read(adminView, "$.results[0].name")).isEqualTo("Beta");

        // shortlisting limits the ballot
        call(put("/api/v1/admin/events/" + eventId + "/teams/" + alphaId), admin,
                "{\"name\":\"Alpha\",\"projectName\":\"A\",\"shortlisted\":true}", 200);
        call(put("/api/v1/me/vote?event=" + SLUG), voter, "{\"teamId\":\"" + betaId + "\"}", 400);

        // after closing, published results are public
        setVoting(now.minusSeconds(7200), now.minusSeconds(60), true);
        String closed = call(get("/api/v1/events/" + SLUG + "/voting"), null, null, 200);
        assertThat((String) JsonPath.read(closed, "$.status")).isEqualTo("CLOSED");
        assertThat((Integer) JsonPath.read(closed, "$.totalVotes")).isEqualTo(2);
        call(put("/api/v1/me/vote?event=" + SLUG), voter, "{\"teamId\":\"" + alphaId + "\"}", 409);

        setVoting(now.minusSeconds(7200), now.minusSeconds(60), false);
        assertThat((Object) JsonPath.read(call(get("/api/v1/events/" + SLUG + "/voting"), null, null, 200), "$.results")).isNull();
    }

    @Test
    void chatAndReactions() throws Exception {
        Cookie ana = register("ana@example.ie", "Ana");
        call(get("/api/v1/events/" + SLUG + "/chat"), null, null, 401);

        String msg = call(post("/api/v1/me/chat?event=" + SLUG), ana, "{\"body\":\"Hello everyone!\"}", 201);
        call(post("/api/v1/me/chat?event=" + SLUG), ana, "{\"body\":\"Too fast\"}", 429);
        call(post("/api/v1/me/chat?event=" + SLUG), admin, "{\"body\":\"Welcome, from the organisers\"}", 201);

        String list = call(get("/api/v1/events/" + SLUG + "/chat"), ana, null, 200);
        assertThat((List<?>) JsonPath.read(list, "$")).hasSize(2);
        assertThat((Boolean) JsonPath.read(list, "$[0].mine")).isTrue();
        assertThat((Boolean) JsonPath.read(list, "$[1].fromOrganiser")).isTrue();

        // only new messages when polling with ?after=
        String after = JsonPath.read(list, "$[1].createdAt");
        assertThat((List<?>) JsonPath.read(call(get("/api/v1/events/" + SLUG + "/chat?after=" + after), ana, null, 200), "$")).isEmpty();

        // organiser removes a message: it stays as a placeholder without its text
        String msgId = JsonPath.read(msg, "$.id");
        call(delete("/api/v1/admin/events/" + eventId + "/chat/" + msgId), ana, null, 403);
        call(delete("/api/v1/admin/events/" + eventId + "/chat/" + msgId), admin, null, 204);
        list = call(get("/api/v1/events/" + SLUG + "/chat"), ana, null, 200);
        assertThat((Boolean) JsonPath.read(list, "$[0].removed")).isTrue();
        assertThat(list).doesNotContain("Hello everyone!");

        // reactions on announcements
        String update = call(post("/api/v1/admin/events/" + eventId + "/announcements"), admin,
                "{\"title\":\"Voting opens at 2pm\"}", 201);
        String updateId = JsonPath.read(update, "$.id");
        call(put("/api/v1/me/announcements/" + updateId + "/reactions/HEART"), ana, null, 204);
        call(put("/api/v1/me/announcements/" + updateId + "/reactions/HEART"), ana, null, 204);
        call(put("/api/v1/me/announcements/" + updateId + "/reactions/PARTY"), admin, null, 204);
        call(put("/api/v1/me/announcements/" + updateId + "/reactions/LAUGH"), ana, null, 400);
        String feed = call(get("/api/v1/events/" + SLUG + "/announcements"), ana, null, 200);
        assertThat((Integer) JsonPath.read(feed, "$[0].reactions.HEART")).isEqualTo(1);
        assertThat((List<String>) JsonPath.read(feed, "$[0].myReactions")).containsExactly("HEART");
        call(delete("/api/v1/me/announcements/" + updateId + "/reactions/HEART"), ana, null, 204);
        feed = call(get("/api/v1/events/" + SLUG + "/announcements"), null, null, 200);
        assertThat((Map<String, Object>) JsonPath.read(feed, "$[0].reactions")).doesNotContainKey("HEART").containsEntry("PARTY", 1);
    }

    @Test
    void deletingAnAccountCleansUpHackathonData() throws Exception {
        Cookie ana = register("ana@example.ie", "Ana");
        Cookie ben = register("ben@example.ie", "Ben");
        String mine = call(post("/api/v1/me/team?event=" + SLUG), ana, "{\"name\":\"Alpha\"}", 200);
        call(post("/api/v1/me/team/join"), ben, "{\"code\":\"" + JsonPath.read(mine, "$.joinCode") + "\"}", 200);
        call(post("/api/v1/me/chat?event=" + SLUG), ana, "{\"body\":\"Bye!\"}", 201);

        String export = call(get("/api/v1/me/export"), ana, null, 200);
        assertThat((List<?>) JsonPath.read(export, "$.teams")).hasSize(1);
        assertThat((List<?>) JsonPath.read(export, "$.chatMessages")).hasSize(1);

        call(delete("/api/v1/me"), ana, null, 204);
        assertThat(jdbc.queryForObject("select count(*) from team_member", Integer.class)).isEqualTo(1);
        String chat = call(get("/api/v1/events/" + SLUG + "/chat"), ben, null, 200);
        assertThat((String) JsonPath.read(chat, "$[0].authorName")).isEqualTo("Former member");
    }
}
