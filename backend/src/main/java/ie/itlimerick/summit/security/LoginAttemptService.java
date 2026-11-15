package ie.itlimerick.summit.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import ie.itlimerick.summit.config.AppProperties;

/**
 * Slows down password guessing: after too many failed logins for an email address, further
 * attempts are refused for a while. In-memory per server instance; the hosting company's
 * firewall / rate limiting should add protection in front of this.
 */
@Service
public class LoginAttemptService {

    private record Attempts(int count, Instant firstFailure) {
    }

    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();
    private final AppProperties props;

    public LoginAttemptService(AppProperties props) {
        this.props = props;
    }

    public boolean isBlocked(String email) {
        Attempts a = failures.get(key(email));
        if (a == null) {
            return false;
        }
        if (a.firstFailure().isBefore(Instant.now().minus(window()))) {
            failures.remove(key(email));
            return false;
        }
        return a.count() >= props.auth().maxFailedLogins();
    }

    public void recordFailure(String email) {
        Instant now = Instant.now();
        failures.compute(key(email), (k, a) -> {
            if (a == null || a.firstFailure().isBefore(now.minus(window()))) {
                return new Attempts(1, now);
            }
            return new Attempts(a.count() + 1, a.firstFailure());
        });
        if (failures.size() > 50_000) {
            failures.entrySet().removeIf(e -> e.getValue().firstFailure().isBefore(now.minus(window())));
        }
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private Duration window() {
        return Duration.ofMinutes(props.auth().lockoutMinutes());
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
