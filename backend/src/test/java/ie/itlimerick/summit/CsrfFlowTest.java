package ie.itlimerick.summit;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the CSRF flow exactly as the browser does it (cookie + X-XSRF-TOKEN header), without
 * Spring's test helpers. Runs in its own application context because those helpers replace the
 * CSRF token store for the context they run in.
 */
@SpringBootTest(properties = "app.privacy-notice-version=csrf-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void browserCsrfFlowWorksAndWrongTokenIsRejected() throws Exception {
        jdbc.update("delete from app_user where email = 'csrf@example.ie'");
        MvcResult csrfResult = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String setCookie = csrfResult.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        String token = setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));
        assertThat(token).isEqualTo(JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token"));
        Cookie xsrf = new Cookie("XSRF-TOKEN", token);
        String body = "{\"email\":\"csrf@example.ie\",\"password\":\"correct-horse-battery\",\"displayName\":\"C\",\"acceptPrivacy\":true}";

        mvc.perform(post("/api/v1/auth/register").cookie(xsrf).header("X-XSRF-TOKEN", "not-the-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/register").cookie(xsrf).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
