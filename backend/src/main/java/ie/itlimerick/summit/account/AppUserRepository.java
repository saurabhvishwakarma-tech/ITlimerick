package ie.itlimerick.summit.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("""
            select u from AppUser u
            where u.platformAdmin = false
              and coalesce(u.lastLoginAt, u.createdAt) < :cutoff
            """)
    List<AppUser> findInactiveNonAdmins(Instant cutoff);
}
