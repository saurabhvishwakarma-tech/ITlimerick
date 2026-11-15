package ie.itlimerick.summit.hackathon;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.hackathon.HackathonDtos.Candidate;
import ie.itlimerick.summit.hackathon.HackathonDtos.Result;
import ie.itlimerick.summit.hackathon.HackathonDtos.VotingStatus;
import ie.itlimerick.summit.hackathon.HackathonDtos.VotingView;
import ie.itlimerick.summit.security.CurrentUser;

/**
 * Community vote: one vote per logged-in person for the project of the day.
 * Organisers set when voting opens and closes. If any teams are shortlisted, only those are on
 * the ballot; otherwise every team is. You can't vote for your own team, and you can change your
 * vote until voting closes. Results are shown to the public only after voting closes, and only if
 * the organisers choose to publish them.
 */
@Service
public class VotingService {

    private final VoteRepository votes;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final HackathonService hackathon;

    public VotingService(VoteRepository votes, TeamRepository teams, TeamMemberRepository members,
                         HackathonService hackathon) {
        this.votes = votes;
        this.teams = teams;
        this.members = members;
        this.hackathon = hackathon;
    }

    @Transactional(readOnly = true)
    public VotingView view(String slug, CurrentUser user) {
        Event event = hackathon.requireHackathon(slug);
        Instant now = Instant.now();
        VotingStatus status = status(event, now);
        UUID myTeam = user == null ? null
                : members.findByEventIdAndUserId(event.getId(), user.id()).map(TeamMember::getTeamId).orElse(null);
        List<Candidate> candidates = ballot(event.getId()).stream()
                .map(t -> new Candidate(t.getId(), t.getName(), t.getProjectName(), t.getDescription(), t.getLogoUrl(),
                        t.getSector(), t.getId().equals(myTeam)))
                .toList();
        UUID myVote = user == null ? null
                : votes.findById(new Vote.Key(event.getId(), user.id())).map(Vote::getTeamId).orElse(null);
        boolean showResults = status == VotingStatus.CLOSED && event.isVotingResultsPublic();
        return new VotingView(status, event.getVotingOpensAt(), event.getVotingClosesAt(), candidates, myVote,
                showResults ? results(event.getId()) : null, showResults ? votes.countByEventId(event.getId()) : 0);
    }

    @Transactional
    public VotingView cast(String slug, CurrentUser user, UUID teamId) {
        Event event = hackathon.requireHackathon(slug);
        if (status(event, Instant.now()) != VotingStatus.OPEN) {
            throw ApiException.conflict("Voting isn't open right now");
        }
        boolean onBallot = ballot(event.getId()).stream().anyMatch(t -> t.getId().equals(teamId));
        if (!onBallot) {
            throw ApiException.badRequest("That project isn't on the ballot");
        }
        Optional<TeamMember> mine = members.findByEventIdAndUserId(event.getId(), user.id());
        if (mine.isPresent() && mine.get().getTeamId().equals(teamId)) {
            throw ApiException.badRequest("You can't vote for your own team");
        }
        Vote vote = votes.findById(new Vote.Key(event.getId(), user.id()))
                .orElseGet(() -> new Vote(event.getId(), user.id(), teamId));
        vote.setTeamId(teamId);
        vote.setVotedAt(Instant.now());
        votes.save(vote);
        return view(slug, user);
    }

    @Transactional
    public VotingView withdraw(String slug, CurrentUser user) {
        Event event = hackathon.requireHackathon(slug);
        if (status(event, Instant.now()) != VotingStatus.OPEN) {
            throw ApiException.conflict("Voting isn't open right now");
        }
        votes.deleteById(new Vote.Key(event.getId(), user.id()));
        return view(slug, user);
    }

    /** Organisers can see live counts at any time. */
    @Transactional(readOnly = true)
    public VotingView adminView(Event event) {
        return new VotingView(status(event, Instant.now()), event.getVotingOpensAt(), event.getVotingClosesAt(),
                List.of(), null, results(event.getId()), votes.countByEventId(event.getId()));
    }

    static VotingStatus status(Event event, Instant now) {
        if (event.getVotingOpensAt() == null || event.getVotingClosesAt() == null) {
            return VotingStatus.NOT_SCHEDULED;
        }
        if (event.isVotingOpen(now)) {
            return VotingStatus.OPEN;
        }
        return event.isVotingClosed(now) ? VotingStatus.CLOSED : VotingStatus.NOT_OPEN;
    }

    private List<Team> ballot(UUID eventId) {
        List<Team> all = teams.findByEventIdOrderByNameAsc(eventId);
        List<Team> shortlisted = all.stream().filter(Team::isShortlisted).toList();
        return shortlisted.isEmpty() ? all : shortlisted;
    }

    private List<Result> results(UUID eventId) {
        Map<UUID, Long> counts = votes.countByTeam(eventId).stream()
                .collect(Collectors.toMap(VoteRepository.TeamCount::getTeamId, VoteRepository.TeamCount::getVotes));
        return teams.findByEventIdOrderByNameAsc(eventId).stream()
                .filter(t -> counts.containsKey(t.getId()) || t.isShortlisted())
                .map(t -> new Result(t.getId(), t.getName(), t.getProjectName(), counts.getOrDefault(t.getId(), 0L)))
                .sorted(Comparator.comparingLong(Result::votes).reversed().thenComparing(Result::name))
                .toList();
    }
}
