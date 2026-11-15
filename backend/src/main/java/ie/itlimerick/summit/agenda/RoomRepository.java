package ie.itlimerick.summit.agenda;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByEventIdOrderBySortOrderAscNameAsc(UUID eventId);
}
