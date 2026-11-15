package ie.itlimerick.summit.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.account.AppUser;
import ie.itlimerick.summit.account.AppUserRepository;
import ie.itlimerick.summit.common.Tokens;
import ie.itlimerick.summit.config.AppProperties;

/**
 * Login sessions backed by the database. The browser holds a random token in an HttpOnly cookie;
 * the database holds only its SHA-256 hash. Sessions survive restarts and work across several
 * server instances, and can be revoked (logout, password change, account deletion).
 */
@Service
public class AuthSessionService {

    private static final Duration SLIDE_AFTER = Duration.ofHours(12);

    private final AuthSessionRepository sessions;
    private final AppUserRepository users;
    private final AppProperties props;

    public AuthSessionService(AuthSessionRepository sessions, AppUserRepository users, AppProperties props) {
        this.sessions = sessions;
        this.users = users;
        this.props = props;
    }

    @Transactional
    public void start(UUID userId, HttpServletResponse response) {
        String token = Tokens.newToken();
        Instant expires = Instant.now().plus(sessionLength());
        sessions.save(new AuthSession(userId, Tokens.sha256(token), expires));
        writeCookie(response, token, sessionLength());
    }

    /** Looks up the user for a session cookie, extending the session if it's being actively used. */
    @Transactional
    public Optional<AppUser> resolve(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return Optional.empty();
        }
        Optional<AuthSession> found = sessions.findByTokenHash(Tokens.sha256(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = found.get();
        Instant now = Instant.now();
        if (session.getExpiresAt().isBefore(now)) {
            sessions.delete(session);
            return Optional.empty();
        }
        if (session.getLastSeenAt().isBefore(now.minus(SLIDE_AFTER))) {
            session.setLastSeenAt(now);
            session.setExpiresAt(now.plus(sessionLength()));
        }
        return users.findById(session.getUserId());
    }

    @Transactional
    public void end(HttpServletRequest request, HttpServletResponse response) {
        readToken(request).ifPresent(token -> sessions.deleteByTokenHash(Tokens.sha256(token)));
        writeCookie(response, "", Duration.ZERO);
    }

    @Transactional
    public void endAll(UUID userId) {
        sessions.deleteByUserId(userId);
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> props.auth().cookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public void clearCookie(HttpServletResponse response) {
        writeCookie(response, "", Duration.ZERO);
    }

    private Duration sessionLength() {
        return Duration.ofDays(Math.max(1, props.auth().sessionDays()));
    }

    private void writeCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(props.auth().cookieName(), value)
                .httpOnly(true)
                .secure(props.auth().cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
