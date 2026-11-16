package ie.itlimerick.summit.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import ie.itlimerick.summit.account.AppUser;
import ie.itlimerick.summit.account.AppUserRepository;
import ie.itlimerick.summit.account.ConsentRecord;
import ie.itlimerick.summit.account.ConsentRecordRepository;
import ie.itlimerick.summit.common.Tokens;
import ie.itlimerick.summit.config.AppProperties;

/**
 * "Continue with Google / LinkedIn" using the OpenID Connect authorization code flow.
 *
 * 1. start(): remember a random state (and PKCE verifier) and send the browser to the provider.
 * 2. The provider sends the browser back to /callback with a one-time code.
 * 3. finish(): check the state, swap the code for an access token (server to server), ask the
 *    provider who the person is, then find or create their account and start a normal session.
 *
 * We keep only the provider's permanent id for the person (and their email/name on the account).
 * Provider tokens are used once and thrown away.
 */
@Service
public class OAuthService {

    public static final List<String> PROVIDERS = List.of("google", "linkedin");
    private static final Duration STATE_VALIDITY = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    /** Why a sign-in failed. The code is shown to the user as a friendly message by the frontend. */
    public static class OAuthFailure extends RuntimeException {
        private final String code;

        OAuthFailure(String code, String detail) {
            super(detail);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Start(String redirectUrl, String state) {
    }

    public record Finish(String returnTo) {
    }

    private record Profile(String subject, String email, boolean emailVerified, String name) {
    }

    private final AppProperties props;
    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final ConsentRecordRepository consents;
    private final AuthSessionService sessions;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public OAuthService(AppProperties props, JdbcClient jdbc, AppUserRepository users, ConsentRecordRepository consents,
                        AuthSessionService sessions, ObjectMapper json) {
        this.props = props;
        this.jdbc = jdbc;
        this.users = users;
        this.consents = consents;
        this.sessions = sessions;
        this.json = json;
    }

    public List<String> enabledProviders() {
        return PROVIDERS.stream().filter(p -> provider(p).map(AppProperties.Provider::enabled).orElse(false)).toList();
    }

    @Transactional
    public Start start(String providerName, String returnTo, UUID linkUserId) {
        AppProperties.Provider p = requireEnabled(providerName);
        String state = Tokens.newToken();
        String verifier = Tokens.newToken() + Tokens.newToken();
        jdbc.sql("""
                        INSERT INTO oauth_login_state (state_hash, provider, code_verifier, return_to, link_user_id, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                .params(Tokens.sha256(state), providerName, verifier, safeReturnTo(returnTo), linkUserId,
                        java.sql.Timestamp.from(Instant.now().plus(STATE_VALIDITY)))
                .update();

        Map<String, String> q = new LinkedHashMap<>();
        q.put("response_type", "code");
        q.put("client_id", p.clientId());
        q.put("redirect_uri", redirectUri(providerName));
        q.put("scope", p.scope());
        q.put("state", state);
        if (p.pkce()) {
            q.put("code_challenge", challenge(verifier));
            q.put("code_challenge_method", "S256");
        }
        if ("google".equals(providerName)) {
            q.put("prompt", "select_account");
        }
        return new Start(p.authorizationUri() + "?" + form(q), state);
    }

    /**
     * @param stateFromCookie the state we stored in the browser when the sign-in started. It must
     *                        match the one the provider sent back, which stops someone tricking a
     *                        browser into finishing a sign-in that it didn't start.
     */
    @Transactional
    public Finish finish(String providerName, String code, String state, String stateFromCookie, String providerError,
                         HttpServletResponse response) {
        if (providerError != null) {
            // The person pressed "Cancel" on the provider's page, or the provider refused.
            throw new OAuthFailure("cancelled", providerError);
        }
        AppProperties.Provider p = requireEnabled(providerName);
        if (code == null || state == null || stateFromCookie == null || !state.equals(stateFromCookie)) {
            throw new OAuthFailure("expired", "missing or mismatched state");
        }
        Map<String, Object> saved = jdbc.sql("""
                        DELETE FROM oauth_login_state WHERE state_hash = ? AND provider = ? AND expires_at > now()
                        RETURNING code_verifier, return_to, link_user_id""")
                .params(Tokens.sha256(state), providerName)
                .query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new OAuthFailure("expired", "unknown or expired state"));

        Profile profile = fetchProfile(providerName, p, code, (String) saved.get("code_verifier"));
        String returnTo = (String) saved.get("return_to");
        UUID linkUserId = (UUID) saved.get("link_user_id");

        Optional<UUID> existing = jdbc.sql("SELECT user_id FROM user_identity WHERE provider = ? AND subject = ?")
                .params(providerName, profile.subject()).query(UUID.class).optional();

        if (linkUserId != null) {
            if (existing.isPresent() && !existing.get().equals(linkUserId)) {
                throw new OAuthFailure("linked-elsewhere", "identity belongs to another account");
            }
            if (existing.isEmpty()) {
                addIdentity(linkUserId, providerName, profile.subject());
            }
            return new Finish("/account?connected=" + providerName);
        }

        AppUser user;
        if (existing.isPresent()) {
            user = users.findById(existing.get()).orElseThrow(() -> new OAuthFailure("failed", "identity without user"));
        } else {
            if (profile.email() == null || !profile.emailVerified()) {
                throw new OAuthFailure("no-email", "provider did not return a verified email");
            }
            String email = profile.email().trim().toLowerCase(Locale.ROOT);
            // Same verified email as an existing account: it's the same person, so connect them.
            user = users.findByEmailIgnoreCase(email).orElseGet(() -> createUser(email, profile.name()));
            addIdentity(user.getId(), providerName, profile.subject());
        }
        if (props.isAdminEmail(user.getEmail()) && !user.isPlatformAdmin()) {
            user.setPlatformAdmin(true);
        }
        user.setLastLoginAt(Instant.now());
        users.save(user);
        sessions.start(user.getId(), response);
        return new Finish(returnTo);
    }

    @Transactional(readOnly = true)
    public List<String> identitiesOf(UUID userId) {
        return jdbc.sql("SELECT provider FROM user_identity WHERE user_id = ? ORDER BY provider")
                .param(userId).query(String.class).list();
    }

    /** Disconnect a provider, unless it's the only way this person can log in. */
    @Transactional
    public void unlink(UUID userId, String providerName) {
        AppUser user = users.findById(userId).orElseThrow();
        List<String> linked = new ArrayList<>(identitiesOf(userId));
        if (!linked.contains(providerName)) {
            return;
        }
        linked.remove(providerName);
        if (linked.isEmpty() && user.getPasswordHash() == null) {
            throw ie.itlimerick.summit.common.ApiException.badRequest(
                    "Set a password first, otherwise you won't be able to log in.");
        }
        jdbc.sql("DELETE FROM user_identity WHERE user_id = ? AND provider = ?").params(userId, providerName).update();
    }

    @Transactional
    public int deleteExpiredStates() {
        return jdbc.sql("DELETE FROM oauth_login_state WHERE expires_at < now()").update();
    }

    public String redirectUri(String providerName) {
        return props.publicBaseUrl().replaceAll("/+$", "") + "/api/v1/auth/oauth/" + providerName + "/callback";
    }

    /** Only paths inside this site, never a full URL, so this can't be used to send people elsewhere. */
    static String safeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")
                || returnTo.length() > 300) {
            return "/";
        }
        return returnTo;
    }

    // ---- internals ----

    private AppUser createUser(String email, String name) {
        AppUser user = new AppUser();
        user.setEmail(email);
        String display = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.strip();
        user.setDisplayName(display.length() > 80 ? display.substring(0, 80) : display);
        user.setPlatformAdmin(props.isAdminEmail(email));
        // Flush now: the identity row is written with plain SQL straight after and needs the user to exist.
        user = users.saveAndFlush(user);
        // The login page says "By continuing you accept the privacy notice" next to these buttons.
        String version = props.privacyNoticeVersion();
        consents.save(new ConsentRecord(user.getId(), ConsentRecord.Type.PRIVACY_NOTICE, version, true));
        consents.save(new ConsentRecord(user.getId(), ConsentRecord.Type.MARKETING, version, false));
        return user;
    }

    private void addIdentity(UUID userId, String providerName, String subject) {
        jdbc.sql("""
                        INSERT INTO user_identity (user_id, provider, subject) VALUES (?, ?, ?)
                        ON CONFLICT (user_id, provider) DO UPDATE SET subject = excluded.subject""")
                .params(userId, providerName, subject).update();
    }

    @SuppressWarnings("unchecked")
    private Profile fetchProfile(String providerName, AppProperties.Provider p, String code, String verifier) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", redirectUri(providerName));
        body.put("client_id", p.clientId());
        body.put("client_secret", p.clientSecret());
        if (p.pkce()) {
            body.put("code_verifier", verifier);
        }
        try {
            HttpResponse<String> tokenResponse = http.send(HttpRequest.newBuilder(URI.create(p.tokenUri()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form(body)))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() != 200) {
                log.warn("{} token exchange failed: HTTP {} {}", providerName, tokenResponse.statusCode(),
                        truncate(tokenResponse.body()));
                throw new OAuthFailure("failed", "token exchange failed");
            }
            Object accessToken = json.readValue(tokenResponse.body(), Map.class).get("access_token");
            if (!(accessToken instanceof String token) || token.isBlank()) {
                throw new OAuthFailure("failed", "no access token");
            }
            HttpResponse<String> infoResponse = http.send(HttpRequest.newBuilder(URI.create(p.userInfoUri()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (infoResponse.statusCode() != 200) {
                log.warn("{} userinfo failed: HTTP {}", providerName, infoResponse.statusCode());
                throw new OAuthFailure("failed", "userinfo failed");
            }
            Map<String, Object> info = json.readValue(infoResponse.body(), Map.class);
            Object sub = info.get("sub");
            if (sub == null || sub.toString().isBlank()) {
                throw new OAuthFailure("failed", "no subject");
            }
            Object verified = info.get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified));
            Object email = info.get("email");
            Object name = info.get("name");
            return new Profile(sub.toString(), email == null ? null : email.toString(), emailVerified,
                    name == null ? null : name.toString());
        } catch (OAuthFailure e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthFailure("failed", "interrupted");
        } catch (Exception e) {
            log.warn("{} sign-in failed: {}", providerName, e.toString());
            throw new OAuthFailure("failed", e.getMessage());
        }
    }

    private AppProperties.Provider requireEnabled(String providerName) {
        return provider(providerName).filter(AppProperties.Provider::enabled)
                .orElseThrow(() -> new OAuthFailure("unavailable", "provider not configured: " + providerName));
    }

    private Optional<AppProperties.Provider> provider(String name) {
        if (props.oauth() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (name) {
            case "google" -> props.oauth().google();
            case "linkedin" -> props.oauth().linkedin();
            default -> null;
        });
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s;
    }
}
