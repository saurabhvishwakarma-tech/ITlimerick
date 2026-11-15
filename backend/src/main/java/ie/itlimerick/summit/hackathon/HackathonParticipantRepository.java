package ie.itlimerick.summit.hackathon;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HackathonParticipantRepository extends JpaRepository<HackathonParticipant, HackathonParticipant.Key> {

    List<HackathonParticipant> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    List<HackathonParticipant> findByUserId(UUID userId);
}
