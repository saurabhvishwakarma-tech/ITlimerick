package ie.itlimerick.summit.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByUserIdOrderByRecordedAtAsc(UUID userId);

    Optional<ConsentRecord> findFirstByUserIdAndTypeOrderByRecordedAtDesc(UUID userId, ConsentRecord.Type type);
}
