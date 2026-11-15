package ie.itlimerick.summit.speaker;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {

    List<Speaker> findByEventIdOrderBySortOrderAscNameAsc(UUID eventId);

    List<Speaker> findByEventIdAndPublishedTrueOrderBySortOrderAscNameAsc(UUID eventId);

    List<Speaker> findByUserId(UUID userId);
}
