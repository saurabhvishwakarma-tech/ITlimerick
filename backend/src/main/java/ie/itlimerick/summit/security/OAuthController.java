package ie.itlimerick.summit.security;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.config.AppProperties;
import ie.itlimerick.summit.security.OAuthService.OAuthFailure;

/**
 * Browser-facing endpoints for "Continue with Google / LinkedIn". These are full-page redirects,
 * not JSON calls: the login button is a plain link to /start.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class OAuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OAuthController.class);
    static final String STATE_COOKIE = "SUMMIT_OAUTH_STATE";
    private static final String COOKIE_PATH = "/api/v1/auth/oauth";

    private final OAuthService oauth;
    private final AppProperties props;

    public OAuthController(OAuthService oauth, AppProperties props) {
        this.oauth = oauth;
        this.props = props;
    }

    /** Which sign-in buttons to show. */
    @GetMapping("/providers")
    Map<String, List<String>> providers() {
        return Map.of("providers", oauth.enabledProviders());
    }

    /**
     * Sends the browser to Google/LinkedIn. ?returnTo=/e/my-event sends them back there afterwards.
     * ?connect=true (when logged in) adds the provider to the current account instead of logging in.
     */
    @GetMapping("/oauth/{provider}/start")
    ResponseEntity<Void> start(@PathVariable String provider, @RequestParam(required = false) String returnTo,
                               @RequestParam(defaultValue = "false") boolean connect,
                               @AuthenticationPrincipal CurrentUser user, HttpServletResponse response) {
        try {
            OAuthService.Start start = oauth.start(provider, returnTo, connect && user != null ? user.id() : null);
            response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(start.state(), Duration.ofMinutes(10)));
            return redirect(start.redirectUrl());
        } catch (OAuthFailure e) {
            return redirect(failureUrl(e.code()));
        }
    }

    /** Where Google/LinkedIn send the browser back to. Register this exact URL with each provider. */
    @GetMapping("/oauth/{provider}/callback")
    ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String error,
                                  HttpServletRequest request, HttpServletResponse response) {
        String cookieState = readStateCookie(request);
        // The state cookie is single-use: clear it whatever happens.
        // Cookies are added straight to the response (never as ResponseEntity headers), because
        // ResponseEntity headers would replace the login cookie that finish() has just added.
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO));
        try {
            OAuthService.Finish finish = oauth.finish(provider, code, state, cookieState, error, response);
            return redirect(finish.returnTo());
        } catch (OAuthFailure e) {
            return redirect(failureUrl(e.code()));
        } catch (RuntimeException e) {
            log.error("{} sign-in failed unexpectedly", provider, e);
            return redirect(failureUrl("failed"));
        }
    }

    private String failureUrl(String code) {
        return "/login?oauthError=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(location))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private String stateCookie(String value, Duration maxAge) {
        // Lax (not Strict) so the cookie comes back when Google/LinkedIn redirect to us.
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(props.auth().cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build().toString();
    }

    private static String readStateCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (STATE_COOKIE.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
