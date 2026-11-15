package ie.itlimerick.summit.hackathon;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.hackathon.HackathonDtos.AddMemberRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.AdminTeamRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.AutoSortRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.AutoSortResult;
import ie.itlimerick.summit.hackathon.HackathonDtos.CastVoteRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.JoinPoolRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.JoinTeamRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.MyHackathonView;
import ie.itlimerick.summit.hackathon.HackathonDtos.ParticipantView;
import ie.itlimerick.summit.hackathon.HackathonDtos.TeamAdminView;
import ie.itlimerick.summit.hackathon.HackathonDtos.TeamRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.TeamView;
import ie.itlimerick.summit.hackathon.HackathonDtos.VotingView;
import ie.itlimerick.summit.security.CurrentUser;

@RestController
public class HackathonController {

    private final HackathonService hackathon;
    private final VotingService voting;
    private final EventService events;

    public HackathonController(HackathonService hackathon, VotingService voting, EventService events) {
        this.hackathon = hackathon;
        this.voting = voting;
        this.events = events;
    }

    // ---- Public (member names only for logged-in people) ----

    @GetMapping("/api/v1/events/{slug}/teams")
    List<TeamView> teams(@PathVariable String slug, @AuthenticationPrincipal CurrentUser user) {
        return hackathon.listTeams(slug, user != null);
    }

    @GetMapping("/api/v1/teams/{teamId}")
    TeamView team(@PathVariable UUID teamId, @AuthenticationPrincipal CurrentUser user) {
        return hackathon.team(teamId, user != null);
    }

    @GetMapping("/api/v1/events/{slug}/voting")
    VotingView voting(@PathVariable String slug, @AuthenticationPrincipal CurrentUser user) {
        return voting.view(slug, user);
    }

    // ---- Me ----

    @GetMapping("/api/v1/me/hackathon")
    MyHackathonView myHackathon(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user) {
        return hackathon.myStatus(slug, user);
    }

    @PutMapping("/api/v1/me/hackathon")
    MyHackathonView joinHackathon(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user,
                                  @Valid @RequestBody JoinPoolRequest req) {
        return hackathon.joinPool(slug, user, req.skills(), req.lookingForTeam());
    }

    @DeleteMapping("/api/v1/me/hackathon")
    ResponseEntity<Void> leaveHackathon(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user) {
        hackathon.leaveHackathon(slug, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/me/team")
    MyHackathonView createTeam(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user,
                               @Valid @RequestBody TeamRequest req) {
        return hackathon.createTeam(slug, user, req);
    }

    @PutMapping("/api/v1/me/team")
    MyHackathonView updateMyTeam(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user,
                                 @Valid @RequestBody TeamRequest req) {
        return hackathon.updateMyTeam(slug, user, req);
    }

    @PostMapping("/api/v1/me/team/join")
    MyHackathonView joinTeam(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody JoinTeamRequest req) {
        return hackathon.joinTeam(user, req.code());
    }

    @PostMapping("/api/v1/me/team/leave")
    MyHackathonView leaveTeam(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user) {
        return hackathon.leaveTeam(slug, user);
    }

    @PutMapping("/api/v1/me/vote")
    VotingView vote(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user,
                    @Valid @RequestBody CastVoteRequest req) {
        return voting.cast(slug, user, req.teamId());
    }

    @DeleteMapping("/api/v1/me/vote")
    VotingView withdrawVote(@RequestParam("event") String slug, @AuthenticationPrincipal CurrentUser user) {
        return voting.withdraw(slug, user);
    }

    // ---- Organisers ----

    @GetMapping("/api/v1/admin/events/{eventId}/teams")
    @PreAuthorize("@access.canManage(#eventId)")
    List<TeamAdminView> adminTeams(@PathVariable UUID eventId) {
        return hackathon.adminTeams(eventId);
    }

    @PostMapping("/api/v1/admin/events/{eventId}/teams")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<TeamAdminView> adminCreateTeam(@PathVariable UUID eventId, @Valid @RequestBody AdminTeamRequest req) {
        return ResponseEntity.status(201).body(hackathon.adminCreateTeam(eventId, req));
    }

    @PutMapping("/api/v1/admin/events/{eventId}/teams/{teamId}")
    @PreAuthorize("@access.canManage(#eventId)")
    TeamAdminView adminUpdateTeam(@PathVariable UUID eventId, @PathVariable UUID teamId,
                                  @Valid @RequestBody AdminTeamRequest req) {
        return hackathon.adminUpdateTeam(eventId, teamId, req);
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/teams/{teamId}")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<Void> adminDeleteTeam(@PathVariable UUID eventId, @PathVariable UUID teamId) {
        hackathon.adminDeleteTeam(eventId, teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/admin/events/{eventId}/teams/{teamId}/members")
    @PreAuthorize("@access.canManage(#eventId)")
    TeamAdminView adminAddMember(@PathVariable UUID eventId, @PathVariable UUID teamId,
                                 @Valid @RequestBody AddMemberRequest req) {
        return hackathon.adminAddMember(eventId, teamId, req.userId());
    }

    @DeleteMapping("/api/v1/admin/events/{eventId}/teams/{teamId}/members/{userId}")
    @PreAuthorize("@access.canManage(#eventId)")
    ResponseEntity<Void> adminRemoveMember(@PathVariable UUID eventId, @PathVariable UUID teamId,
                                           @PathVariable UUID userId) {
        hackathon.adminRemoveMember(eventId, teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/admin/events/{eventId}/participants")
    @PreAuthorize("@access.canManage(#eventId)")
    List<ParticipantView> adminParticipants(@PathVariable UUID eventId) {
        return hackathon.adminParticipants(eventId);
    }

    @PostMapping("/api/v1/admin/events/{eventId}/teams/auto-sort")
    @PreAuthorize("@access.canManage(#eventId)")
    AutoSortResult autoSort(@PathVariable UUID eventId, @Valid @RequestBody AutoSortRequest req) {
        return hackathon.autoSort(eventId, req.teamSize());
    }

    @GetMapping("/api/v1/admin/events/{eventId}/votes")
    @PreAuthorize("@access.canManage(#eventId)")
    VotingView adminVotes(@PathVariable UUID eventId) {
        return voting.adminView(events.require(eventId));
    }
}
