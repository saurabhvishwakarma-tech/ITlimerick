package ie.itlimerick.summit.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under the "app" prefix in application.yml. */
@ConfigurationProperties("app")
public record AppProperties(
        String publicBaseUrl,
        List<String> adminEmails,
        String privacyNoticeVersion,
        Auth auth,
        Email email,
        Retention retention,
        OAuth oauth) {

    public record Auth(String cookieName, boolean cookieSecure, int sessionDays, int maxFailedLogins, int lockoutMinutes) {
    }

    /** logContent=true writes the full email (including reset links) to the log. Only for testing. */
    public record Email(String webhookUrl, String webhookToken, boolean logContent) {
    }

    /** Google and LinkedIn sign-in. A provider is switched on by setting its client id and secret. */
    public record OAuth(Provider google, Provider linkedin) {
    }

    public record Provider(String clientId, String clientSecret, String authorizationUri, String tokenUri,
                           String userInfoUri, String scope, boolean pkce) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Retention(int deleteInactiveUsersAfterDays) {
    }

    public boolean isAdminEmail(String email) {
        if (adminEmails == null || email == null) {
            return false;
        }
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        return adminEmails.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .anyMatch(normalised::equals);
    }
}
