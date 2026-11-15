package ie.itlimerick.summit.schedule;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.Key> {

    List<Bookmark> findByUserId(UUID userId);

    @Query("""
            select b.sessionId from Bookmark b, AgendaSession s
            where b.sessionId = s.id and b.userId = :userId and s.eventId = :eventId
            """)
    List<UUID> findSessionIds(UUID userId, UUID eventId);
}
