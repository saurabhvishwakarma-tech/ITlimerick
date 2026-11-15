package ie.itlimerick.summit.sponsor;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SponsorRepository extends JpaRepository<Sponsor, UUID> {

    List<Sponsor> findByEventIdOrderByTierRankAscSortOrderAscNameAsc(UUID eventId);
}
