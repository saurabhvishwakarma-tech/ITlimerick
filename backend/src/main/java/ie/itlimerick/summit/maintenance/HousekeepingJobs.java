package ie.itlimerick.summit.maintenance;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.account.AccountService;
import ie.itlimerick.summit.account.AppUser;
import ie.itlimerick.summit.account.AppUserRepository;
import ie.itlimerick.summit.config.AppProperties;
import ie.itlimerick.summit.security.AuthSessionRepository;
import ie.itlimerick.summit.security.PasswordResetTokenRepository;

/**
 * Nightly clean-up. Safe to run on several instances at once: every step is an idempotent delete.
 */
@Component
public class HousekeepingJobs {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingJobs.class);

    private final AuthSessionRepository authSessions;
    private final PasswordResetTokenRepository resetTokens;
    private final AppUserRepository users;
    private final AccountService accounts;
    private final AppProperties props;

    public HousekeepingJobs(AuthSessionRepository authSessions, PasswordResetTokenRepository resetTokens,
                            AppUserRepository users, AccountService accounts, AppProperties props) {
        this.authSessions = authSessions;
        this.resetTokens = resetTokens;
        this.users = users;
        this.accounts = accounts;
        this.props = props;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Dublin")
    @Transactional
    public void removeExpiredTokens() {
        Instant now = Instant.now();
        int sessions = authSessions.deleteExpired(now);
        int resets = resetTokens.deleteExpired(now);
        log.info("Housekeeping: removed {} expired login sessions and {} expired reset links", sessions, resets);
    }

    /** GDPR data minimisation: optionally delete accounts nobody has used for a long time. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Dublin")
    public void applyRetentionPolicy() {
        int days = props.retention() == null ? 0 : props.retention().deleteInactiveUsersAfterDays();
        if (days <= 0) {
            return;
        }
        List<AppUser> inactive = users.findInactiveNonAdmins(Instant.now().minus(Duration.ofDays(days)));
        inactive.forEach(u -> accounts.deleteAccount(u.getId()));
        if (!inactive.isEmpty()) {
            log.info("Retention: deleted {} accounts inactive for more than {} days", inactive.size(), days);
        }
    }
}
