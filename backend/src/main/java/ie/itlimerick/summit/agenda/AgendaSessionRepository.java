package ie.itlimerick.summit.agenda;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgendaSessionRepository extends JpaRepository<AgendaSession, UUID> {

    List<AgendaSession> findByEventIdOrderByStartsAtAscTitleAsc(UUID eventId);

    @Query("select s from AgendaSession s join s.speakerIds sp where sp = :speakerId order by s.startsAt")
    List<AgendaSession> findBySpeakerId(UUID speakerId);

    List<AgendaSession> findByIdIn(Collection<UUID> ids);
}
