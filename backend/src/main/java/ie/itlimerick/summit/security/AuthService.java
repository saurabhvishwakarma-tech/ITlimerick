package ie.itlimerick.summit.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.account.AppUser;
import ie.itlimerick.summit.account.AppUserRepository;
import ie.itlimerick.summit.account.ConsentRecord;
import ie.itlimerick.summit.account.ConsentRecordRepository;
import ie.itlimerick.summit.account.EventMembership;
import ie.itlimerick.summit.account.EventMembershipRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.common.Tokens;
import ie.itlimerick.summit.config.AppProperties;
import ie.itlimerick.summit.email.EmailService;
import ie.itlimerick.summit.security.AuthDtos.LoginRequest;
import ie.itlimerick.summit.security.AuthDtos.RegisterRequest;
import ie.itlimerick.summit.security.AuthDtos.UserView;

@Service
public class AuthService {

    private static final Duration RESET_VALIDITY = Duration.ofHours(1);

    private final AppUserRepository users;
    private final EventMembershipRepository memberships;
    private final ConsentRecordRepository consents;
    private final PasswordResetTokenRepository resetTokens;
    private final AuthSessionService sessions;
    private final LoginAttemptService attempts;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final AppProperties props;
    private final String dummyHash;

    public AuthService(AppUserRepository users, EventMembershipRepository memberships, ConsentRecordRepository consents,
                       PasswordResetTokenRepository resetTokens, AuthSessionService sessions, LoginAttemptService attempts,
                       PasswordEncoder encoder, EmailService email, AppProperties props) {
        this.users = users;
        this.memberships = memberships;
        this.consents = consents;
        this.resetTokens = resetTokens;
        this.sessions = sessions;
        this.attempts = attempts;
        this.encoder = encoder;
        this.email = email;
        this.props = props;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-password");
    }

    @Transactional
    public UserView register(RegisterRequest req, HttpServletResponse response) {
        String normalisedEmail = normalise(req.email());
        if (users.findByEmailIgnoreCase(normalisedEmail).isPresent()) {
            throw ApiException.conflict("An account with this email already exists. Try logging in instead.");
        }
        AppUser user = new AppUser();
        user.setEmail(normalisedEmail);
        user.setDisplayName(req.displayName().trim());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setPlatformAdmin(props.isAdminEmail(normalisedEmail));
        user.setLastLoginAt(Instant.now());
        user = users.save(user);

        String version = props.privacyNoticeVersion();
        consents.save(new ConsentRecord(user.getId(), ConsentRecord.Type.PRIVACY_NOTICE, version, true));
        consents.save(new ConsentRecord(user.getId(), ConsentRecord.Type.MARKETING, version, req.marketingOptIn()));

        sessions.start(user.getId(), response);
        return view(user);
    }

    @Transactional
    public UserView login(LoginRequest req, HttpServletResponse response) {
        String normalisedEmail = normalise(req.email());
        if (attempts.isBlocked(normalisedEmail)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Please wait a few minutes and try again.");
        }
        AppUser user = users.findByEmailIgnoreCase(normalisedEmail).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash;
        boolean matches = encoder.matches(req.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches) {
            attempts.recordFailure(normalisedEmail);
            throw ApiException.unauthorized("Email or password is incorrect");
        }
        attempts.recordSuccess(normalisedEmail);
        if (props.isAdminEmail(normalisedEmail) && !user.isPlatformAdmin()) {
            user.setPlatformAdmin(true);
        }
        user.setLastLoginAt(Instant.now());
        sessions.start(user.getId(), response);
        return view(user);
    }

    /** Always succeeds from the caller's point of view, so it can't be used to find out who has an account. */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        users.findByEmailIgnoreCase(normalise(rawEmail)).ifPresent(user -> {
            String token = Tokens.newToken();
            resetTokens.save(new PasswordResetToken(user.getId(), Tokens.sha256(token), Instant.now().plus(RESET_VALIDITY)));
            String link = props.publicBaseUrl().replaceAll("/+$", "") + "/reset-password?token=" + token;
            email.send(user.getEmail(), "Reset your password",
                    "Hi " + user.getDisplayName() + ",\n\n"
                            + "Use this link within the next hour to choose a new password:\n" + link + "\n\n"
                            + "If you didn't ask for this, you can ignore this email.");
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken reset = resetTokens.findByTokenHash(Tokens.sha256(token))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired. Please request a new one."));
        AppUser user = users.findById(reset.getUserId()).orElseThrow(() -> ApiException.notFound("User"));
        user.setPasswordHash(encoder.encode(newPassword));
        reset.setUsedAt(Instant.now());
        sessions.endAll(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, HttpServletResponse response) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        // People who signed up with Google/LinkedIn have no password yet; they can set one without a current one.
        if (user.getPasswordHash() != null
                && (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash()))) {
            throw ApiException.badRequest("Your current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        sessions.endAll(userId);
        sessions.start(userId, response);
    }

    @Transactional(readOnly = true)
    public UserView view(UUID userId) {
        return view(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
    }

    UserView view(AppUser user) {
        List<UUID> organiserOf = memberships.findByUserId(user.getId()).stream()
                .filter(m -> m.getRole() == EventMembership.Role.ORGANISER)
                .map(EventMembership::getEventId)
                .toList();
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.isPlatformAdmin(), organiserOf);
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
