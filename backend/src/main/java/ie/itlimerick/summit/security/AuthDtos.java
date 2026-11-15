package ie.itlimerick.summit.security;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128, message = "Use at least 10 characters") String password,
            @NotBlank @Size(max = 80) String displayName,
            @AssertTrue(message = "Please accept the privacy notice") boolean acceptPrivacy,
            boolean marketingOptIn) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 10, max = 128, message = "Use at least 10 characters") String newPassword) {
    }

    /** What the frontend needs to know about the logged-in user. */
    public record UserView(UUID id, String email, String displayName, boolean platformAdmin, List<UUID> organiserOf) {
    }

    public record SessionView(UserView user) {
    }

    public record CsrfView(String token) {
    }
}
