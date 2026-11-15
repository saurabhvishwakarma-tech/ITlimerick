package ie.itlimerick.summit.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.security.AuthDtos.CsrfView;
import ie.itlimerick.summit.security.AuthDtos.ForgotPasswordRequest;
import ie.itlimerick.summit.security.AuthDtos.LoginRequest;
import ie.itlimerick.summit.security.AuthDtos.RegisterRequest;
import ie.itlimerick.summit.security.AuthDtos.ResetPasswordRequest;
import ie.itlimerick.summit.security.AuthDtos.SessionView;
import ie.itlimerick.summit.security.AuthDtos.UserView;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthSessionService sessions;

    public AuthController(AuthService auth, AuthSessionService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    /** Gives the frontend a CSRF token (also set as the XSRF-TOKEN cookie). */
    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getToken());
    }

    /** Who is logged in, if anyone. Returns {"user": null} rather than 401 when logged out. */
    @GetMapping("/session")
    SessionView session(@AuthenticationPrincipal CurrentUser user) {
        return new SessionView(user == null ? null : auth.view(user.id()));
    }

    @PostMapping("/register")
    ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        return ResponseEntity.status(201).body(auth.register(req, response));
    }

    @PostMapping("/login")
    UserView login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        return auth.login(req, response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.end(request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.requestPasswordReset(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.noContent().build();
    }
}
