package ie.itlimerick.summit.hackathon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMember.Key> {

    List<TeamMember> findByEventId(UUID eventId);

    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(UUID teamId);

    Optional<TeamMember> findByEventIdAndUserId(UUID eventId, UUID userId);

    List<TeamMember> findByUserId(UUID userId);

    long countByTeamId(UUID teamId);
}
