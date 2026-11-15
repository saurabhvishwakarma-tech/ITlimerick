package ie.itlimerick.summit.event;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.event.EventDtos.EventRequest;
import ie.itlimerick.summit.event.EventDtos.EventView;
import ie.itlimerick.summit.security.CurrentUser;

@RestController
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    // ---- Public ----

    @GetMapping("/api/v1/events")
    List<EventView> listPublished() {
        return events.listPublished();
    }

    @GetMapping("/api/v1/events/{slug}")
    EventView get(@PathVariable String slug) {
        return EventView.of(events.requirePublished(slug));
    }

    // ---- Admin ----

    @GetMapping("/api/v1/admin/events")
    List<EventView> listManageable(@AuthenticationPrincipal CurrentUser user) {
        return events.listManageable(user);
    }

    @GetMapping("/api/v1/admin/events/{eventId}")
    @PreAuthorize("@access.canManage(#eventId)")
    EventView getForAdmin(@PathVariable UUID eventId) {
        return EventView.of(events.require(eventId));
    }

    @PostMapping("/api/v1/admin/events")
    @PreAuthorize("@access.isPlatformAdmin()")
    ResponseEntity<EventView> create(@Valid @RequestBody EventRequest req) {
        return ResponseEntity.status(201).body(events.create(req));
    }

    @PutMapping("/api/v1/admin/events/{eventId}")
    @PreAuthorize("@access.canManage(#eventId)")
    EventView update(@PathVariable UUID eventId, @Valid @RequestBody EventRequest req) {
        return events.update(eventId, req);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}")
    @PreAuthorize("@access.isPlatformAdmin()")
    ResponseEntity<Void> delete(@PathVariable UUID eventId) {
        events.delete(eventId);
        return ResponseEntity.noContent().build();
    }
}
