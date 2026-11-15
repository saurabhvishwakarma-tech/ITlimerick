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
        Retention retention) {

    public record Auth(String cookieName, boolean cookieSecure, int sessionDays, int maxFailedLogins, int lockoutMinutes) {
    }

    public record Email(String webhookUrl, String webhookToken) {
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
