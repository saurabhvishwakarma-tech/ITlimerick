package ie.itlimerick.summit.agenda;

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

import ie.itlimerick.summit.agenda.AgendaDtos.RoomRequest;
import ie.itlimerick.summit.agenda.AgendaDtos.RoomView;
import ie.itlimerick.summit.agenda.AgendaDtos.SessionRequest;
import ie.itlimerick.summit.agenda.AgendaDtos.SessionView;

@RestController
public class AgendaController {

    private final AgendaService agenda;

    public AgendaController(AgendaService agenda) {
        this.agenda = agenda;
    }

    // ---- Public ----

    @GetMapping("/api/v1/events/{slug}/sessions")
    List<SessionView> sessions(@PathVariable String slug) {
        return agenda.publicAgenda(slug);
    }

    @GetMapping("/api/v1/events/{slug}/rooms")
    List<RoomView> rooms(@PathVariable String slug) {
        return agenda.publicRooms(slug);
    }

    @GetMapping("/api/v1/sessions/{sessionId}")
    SessionView session(@PathVariable UUID sessionId) {
        return agenda.publicSession(sessionId);
    }

    // ---- Admin: sessions ----

    @GetMapping("/api/v1/admin/events/{eventId}/sessions")
    @PreAuthorize("@access.canManage(#eventId)")
    List<SessionView> adminSessions(@PathVariable UUID eventId) {
        return agenda.adminAgenda(eventId);
    }

    @PostMapping("/api/v1/admin/events/{eventId}/sessions")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<SessionView> createSession(@PathVariable UUID eventId, @Valid @RequestBody SessionRequest req) {
        return ResponseEntity.status(201).body(agenda.createSession(eventId, req));
    }

    @PutMapping("/api/v1/admin/events/{eventId}/sessions/{sessionId}")
    @PreAuthorize("@access.canManage(#eventId)")
    SessionView updateSession(@PathVariable UUID eventId, @PathVariable UUID sessionId,
                              @Valid @RequestBody SessionRequest req) {
        return agenda.updateSession(eventId, sessionId, req);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/sessions/{sessionId}")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<Void> deleteSession(@PathVariable UUID eventId, @PathVariable UUID sessionId) {
        agenda.deleteSession(eventId, sessionId);
        return ResponseEntity.noContent().build();
    }

    // ---- Admin: rooms ----

    @GetMapping("/api/v1/admin/events/{eventId}/rooms")
    @PreAuthorize("@access.canManage(#eventId)")
    List<RoomView> adminRooms(@PathVariable UUID eventId) {
        return agenda.listRooms(eventId);
    }

    @PostMapping("/api/v1/admin/events/{eventId}/rooms")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<RoomView> createRoom(@PathVariable UUID eventId, @Valid @RequestBody RoomRequest req) {
        return ResponseEntity.status(201).body(agenda.createRoom(eventId, req));
    }

    @PutMapping("/api/v1/admin/events/{eventId}/rooms/{roomId}")
    @PreAuthorize("@access.canManage(#eventId)")
    RoomView updateRoom(@PathVariable UUID eventId, @PathVariable UUID roomId, @Valid @RequestBody RoomRequest req) {
        return agenda.updateRoom(eventId, roomId, req);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/rooms/{roomId}")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<Void> deleteRoom(@PathVariable UUID eventId, @PathVariable UUID roomId) {
        agenda.deleteRoom(eventId, roomId);
        return ResponseEntity.noContent().build();
    }
}
