package ie.itlimerick.summit.speaker;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerDetail;
import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerRequest;
import ie.itlimerick.summit.speaker.SpeakerDtos.SpeakerView;

@RestController
public class SpeakerController {

    private final SpeakerService speakers;

    public SpeakerController(SpeakerService speakers) {
        this.speakers = speakers;
    }

    @GetMapping("/api/v1/events/{slug}/speakers")
    List<SpeakerView> list(@PathVariable String slug) {
        return speakers.publicList(slug);
    }

    @GetMapping("/api/v1/speakers/{speakerId}")
    SpeakerDetail detail(@PathVariable UUID speakerId) {
        return speakers.publicDetail(speakerId);
    }

    @GetMapping("/api/v1/admin/events/{eventId}/speakers")
    @PreAuthorize("@access.canManage(#eventId)")
    List<SpeakerView> adminList(@PathVariable UUID eventId) {
        return speakers.adminList(eventId);
    }

    @PostMapping("/api/v1/admin/events/{eventId}/speakers")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<SpeakerView> create(@PathVariable UUID eventId, @Valid @RequestBody SpeakerRequest req) {
        return ResponseEntity.status(201).body(speakers.create(eventId, req));
    }

    @PutMapping("/api/v1/admin/events/{eventId}/speakers/{speakerId}")
    @PreAuthorize("@access.canManage(#eventId)")
    SpeakerView update(@PathVariable UUID eventId, @PathVariable UUID speakerId, @Valid @RequestBody SpeakerRequest req) {
        return speakers.update(eventId, speakerId, req);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/speakers/{speakerId}")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID speakerId) {
        speakers.delete(eventId, speakerId);
        return ResponseEntity.noContent().build();
    }
}
