package ie.itlimerick.summit.sponsor;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.common.Urls;
import ie.itlimerick.summit.event.EventService;

import static ie.itlimerick.summit.event.EventService.blankToNull;

/** Sponsors are simple records, so the logic lives here rather than in a separate service. */
@RestController
public class SponsorController {

    public record SponsorView(UUID id, String name, String tier, int tierRank, String description, String logoUrl,
                              String websiteUrl, int sortOrder) {
        static SponsorView of(Sponsor s) {
            return new SponsorView(s.getId(), s.getName(), s.getTier(), s.getTierRank(), s.getDescription(),
                    s.getLogoUrl(), s.getWebsiteUrl(), s.getSortOrder());
        }
    }

    public record SponsorRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 60) String tier,
            int tierRank,
            @Size(max = 3000) String description,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String logoUrl,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String websiteUrl,
            int sortOrder) {
    }

    private final SponsorRepository sponsors;
    private final EventService events;

    public SponsorController(SponsorRepository sponsors, EventService events) {
        this.sponsors = sponsors;
        this.events = events;
    }

    @GetMapping("/api/v1/events/{slug}/sponsors")
    @Transactional(readOnly = true)
    public List<SponsorView> list(@PathVariable String slug) {
        UUID eventId = events.requirePublished(slug).getId();
        return sponsors.findByEventIdOrderByTierRankAscSortOrderAscNameAsc(eventId).stream().map(SponsorView::of).toList();
    }

    @GetMapping("/api/v1/admin/events/{eventId}/sponsors")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional(readOnly = true)
    public List<SponsorView> adminList(@PathVariable UUID eventId) {
        return sponsors.findByEventIdOrderByTierRankAscSortOrderAscNameAsc(eventId).stream().map(SponsorView::of).toList();
    }

    @PostMapping("/api/v1/admin/events/{eventId}/sponsors")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public ResponseEntity<SponsorView> create(@PathVariable UUID eventId, @Valid @RequestBody SponsorRequest req) {
        events.require(eventId);
        Sponsor s = new Sponsor();
        s.setEventId(eventId);
        apply(s, req);
        return ResponseEntity.status(201).body(SponsorView.of(sponsors.save(s)));
    }

    @PutMapping("/api/v1/admin/events/{eventId}/sponsors/{sponsorId}")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public SponsorView update(@PathVariable UUID eventId, @PathVariable UUID sponsorId,
                              @Valid @RequestBody SponsorRequest req) {
        Sponsor s = require(eventId, sponsorId);
        apply(s, req);
        return SponsorView.of(s);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/sponsors/{sponsorId}")
    @PreAuthorize("@access.canManage(#eventId)")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID eventId, @PathVariable UUID sponsorId) {
        sponsors.delete(require(eventId, sponsorId));
        return ResponseEntity.noContent().build();
    }

    private Sponsor require(UUID eventId, UUID sponsorId) {
        return sponsors.findById(sponsorId).filter(s -> s.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Sponsor"));
    }

    private void apply(Sponsor s, SponsorRequest req) {
        s.setName(req.name().trim());
        s.setTier(req.tier().trim());
        s.setTierRank(req.tierRank());
        s.setDescription(blankToNull(req.description()));
        s.setLogoUrl(blankToNull(req.logoUrl()));
        s.setWebsiteUrl(blankToNull(req.websiteUrl()));
        s.setSortOrder(req.sortOrder());
    }
}
