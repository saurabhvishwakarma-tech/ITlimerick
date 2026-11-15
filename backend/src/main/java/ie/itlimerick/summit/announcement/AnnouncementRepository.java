package ie.itlimerick.summit.announcement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    /** Announcements already live (published time has passed), pinned first, newest first. */
    @Query("""
            select a from Announcement a
            where a.eventId = :eventId and a.publishedAt <= :now
            order by a.pinned desc, a.publishedAt desc
            """)
    List<Announcement> findLive(UUID eventId, Instant now, Limit limit);

    List<Announcement> findByEventIdOrderByPublishedAtDesc(UUID eventId);
}
