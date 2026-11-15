package ie.itlimerick.summit.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import ie.itlimerick.summit.account.EventMembership;
import ie.itlimerick.summit.account.EventMembershipRepository;

/**
 * Permission checks used in {@code @PreAuthorize} expressions, e.g.
 * {@code @PreAuthorize("@access.canManage(#eventId)")}.
 */
@Component("access")
public class AccessService {

    private final EventMembershipRepository memberships;

    public AccessService(EventMembershipRepository memberships) {
        this.memberships = memberships;
    }

    public Optional<CurrentUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public boolean isPlatformAdmin() {
        return currentUser().map(CurrentUser::platformAdmin).orElse(false);
    }

    /** Platform admins can manage every event; organisers only the events they organise. */
    public boolean canManage(UUID eventId) {
        return currentUser()
                .map(u -> u.platformAdmin()
                        || memberships.existsByUserIdAndEventIdAndRole(u.id(), eventId, EventMembership.Role.ORGANISER))
                .orElse(false);
    }
}
