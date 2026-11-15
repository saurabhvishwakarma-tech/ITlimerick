package ie.itlimerick.summit.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads the session cookie on each request and, if valid, marks the request as logged in. */
public class SessionAuthFilter extends OncePerRequestFilter {

    private final AuthSessionService sessions;

    public SessionAuthFilter(AuthSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        sessions.readToken(request)
                .flatMap(sessions::resolve)
                .ifPresent(user -> {
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                    if (user.isPlatformAdmin()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
                    }
                    CurrentUser principal = new CurrentUser(
                            user.getId(), user.getEmail(), user.getDisplayName(), user.isPlatformAdmin());
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
                    SecurityContextHolder.setContext(context);
                });
        chain.doFilter(request, response);
    }
}
