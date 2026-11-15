package ie.itlimerick.summit.hackathon;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VoteRepository extends JpaRepository<Vote, Vote.Key> {

    interface TeamCount {
        UUID getTeamId();
        long getVotes();
    }

    @Query("select v.teamId as teamId, count(v) as votes from Vote v where v.eventId = :eventId group by v.teamId")
    List<TeamCount> countByTeam(UUID eventId);

    long countByEventId(UUID eventId);

    List<Vote> findByUserId(UUID userId);
}
