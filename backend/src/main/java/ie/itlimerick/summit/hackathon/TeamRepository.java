package ie.itlimerick.summit.hackathon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByEventIdOrderByNameAsc(UUID eventId);

    Optional<Team> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    long countByEventId(UUID eventId);
}
