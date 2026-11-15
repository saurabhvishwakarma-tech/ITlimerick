package ie.itlimerick.summit.chat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementReactionRepository extends JpaRepository<AnnouncementReaction, AnnouncementReaction.Key> {

    List<AnnouncementReaction> findByAnnouncementIdIn(Collection<UUID> announcementIds);

    List<AnnouncementReaction> findByUserId(UUID userId);
}
