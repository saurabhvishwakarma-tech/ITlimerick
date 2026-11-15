package ie.itlimerick.summit.account;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.account.AccountService.ExportView;
import ie.itlimerick.summit.account.AccountService.MemberView;
import ie.itlimerick.summit.account.AccountService.ProfileView;
import ie.itlimerick.summit.security.AuthService;
import ie.itlimerick.summit.security.AuthSessionService;
import ie.itlimerick.summit.security.CurrentUser;

@RestController
public class MeController {

    public record UpdateProfileRequest(@NotBlank @Size(max = 80) String displayName) {
    }

    public record ConsentRequest(boolean marketing) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128, message = "Use at least 10 characters") String newPassword) {
    }

    public record AddMemberRequest(@NotBlank @Email String email, @NotNull EventMembership.Role role) {
    }

    private final AccountService accounts;
    private final AuthService auth;
    private final AuthSessionService sessions;

    public MeController(AccountService accounts, AuthService auth, AuthSessionService sessions) {
        this.accounts = accounts;
        this.auth = auth;
        this.sessions = sessions;
    }

    @GetMapping("/api/v1/me")
    ProfileView me(@AuthenticationPrincipal CurrentUser user) {
        return accounts.profile(user.id());
    }

    @PatchMapping("/api/v1/me")
    ProfileView update(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UpdateProfileRequest req) {
        return accounts.updateDisplayName(user.id(), req.displayName());
    }

    @PutMapping("/api/v1/me/consents")
    ProfileView consents(@AuthenticationPrincipal CurrentUser user, @RequestBody ConsentRequest req) {
        return accounts.setMarketingConsent(user.id(), req.marketing());
    }

    @PutMapping("/api/v1/me/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal CurrentUser user,
                                        @Valid @RequestBody ChangePasswordRequest req, HttpServletResponse response) {
        auth.changePassword(user.id(), req.currentPassword(), req.newPassword(), response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/me/export")
    ResponseEntity<ExportView> export(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-summit-data.json\"")
                .body(accounts.export(user.id()));
    }

    @DeleteMapping("/api/v1/me")
    ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, HttpServletResponse response) {
        accounts.deleteAccount(user.id());
        sessions.clearCookie(response);
        return ResponseEntity.noContent().build();
    }

    // ---- Event members (organisers manage who is an organiser / speaker / sponsor) ----

    @GetMapping("/api/v1/admin/events/{eventId}/members")
    @PreAuthorize("@access.canManage(#eventId)")
    List<MemberView> members(@PathVariable UUID eventId) {
        return accounts.members(eventId);
    }

    @PostMapping("/api/v1/admin/events/{eventId}/members")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<MemberView> addMember(@PathVariable UUID eventId, @Valid @RequestBody AddMemberRequest req) {
        return ResponseEntity.status(201).body(accounts.addMember(eventId, req.email(), req.role()));
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/members/{membershipId}")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<Void> removeMember(@PathVariable UUID eventId, @PathVariable UUID membershipId) {
        accounts.removeMember(eventId, membershipId);
        return ResponseEntity.noContent().build();
    }
}
