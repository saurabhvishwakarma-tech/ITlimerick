package ie.itlimerick.summit.event;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Event> findByStatusOrderByStartsAtDesc(Event.Status status);

    List<Event> findAllByOrderByStartsAtDesc();

    List<Event> findByIdInOrderByStartsAtDesc(Collection<UUID> ids);
}
