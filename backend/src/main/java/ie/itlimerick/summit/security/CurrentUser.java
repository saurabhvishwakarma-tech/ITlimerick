package ie.itlimerick.summit.security;

import java.util.UUID;

/** The logged-in user, available in controllers via {@code @AuthenticationPrincipal CurrentUser user}. */
public record CurrentUser(UUID id, String email, String displayName, boolean platformAdmin) {
}
