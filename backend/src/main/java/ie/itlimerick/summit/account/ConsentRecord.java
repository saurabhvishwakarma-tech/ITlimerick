package ie.itlimerick.summit.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only GDPR record: what a user agreed to, when, and which privacy notice version. */
@Entity
@Table(name = "consent_record")
public class ConsentRecord {

    public enum Type { PRIVACY_NOTICE, MARKETING }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "notice_version", nullable = false)
    private String noticeVersion;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt = Instant.now();

    protected ConsentRecord() {
    }

    public ConsentRecord(UUID userId, Type type, String noticeVersion, boolean granted) {
        this.userId = userId;
        this.type = type;
        this.noticeVersion = noticeVersion;
        this.granted = granted;
    }

    public Type getType() {
        return type;
    }

    public String getNoticeVersion() {
        return noticeVersion;
    }

    public boolean isGranted() {
        return granted;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
