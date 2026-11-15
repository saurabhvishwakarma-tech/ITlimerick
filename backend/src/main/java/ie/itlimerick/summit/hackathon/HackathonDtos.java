package ie.itlimerick.summit.hackathon;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ie.itlimerick.summit.common.Urls;

public final class HackathonDtos {

    private HackathonDtos() {
    }

    /** Public view of a team. Member names are only included for logged-in viewers. */
    public record TeamView(UUID id, String name, String projectName, String description, String sector, String logoUrl,
                           String projectUrl, boolean shortlisted, int memberCount, List<String> memberNames) {
    }

    public record MemberView(UUID userId, String displayName, String email, TeamMember.Role role) {
    }

    /** Organiser view: includes join code and member contact details. */
    public record TeamAdminView(UUID id, String name, String projectName, String description, String sector,
                                String logoUrl, String projectUrl, boolean shortlisted, String joinCode,
                                List<MemberView> members) {
    }

    public record ParticipantView(UUID userId, String displayName, String email, String skills, boolean lookingForTeam,
                                  UUID teamId, String teamName, Instant joinedAt) {
    }

    /** Everything the logged-in user needs to see about their own hackathon status. */
    public record MyHackathonView(boolean participant, String skills, boolean lookingForTeam, TeamView team,
                                  TeamMember.Role myRole, String joinCode, int teamMaxSize) {
    }

    public record JoinPoolRequest(@Size(max = 300) String skills, boolean lookingForTeam) {
    }

    public record TeamRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 150) String projectName,
            @Size(max = 3000) String description,
            @Size(max = 80) String sector,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String logoUrl,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String projectUrl) {
    }

    public record AdminTeamRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 150) String projectName,
            @Size(max = 3000) String description,
            @Size(max = 80) String sector,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String logoUrl,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String projectUrl,
            boolean shortlisted) {
    }

    public record JoinTeamRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record AddMemberRequest(@NotNull UUID userId) {
    }

    public record AutoSortRequest(@Min(2) @Max(50) Integer teamSize) {
    }

    public record AutoSortResult(int assigned, int teamsCreated, int teamsFilled) {
    }

    // ---- Voting ----

    public enum VotingStatus { NOT_SCHEDULED, NOT_OPEN, OPEN, CLOSED }

    public record Candidate(UUID teamId, String name, String projectName, String description, String logoUrl,
                            String sector, boolean isMyTeam) {
    }

    public record Result(UUID teamId, String name, String projectName, long votes) {
    }

    public record VotingView(VotingStatus status, Instant opensAt, Instant closesAt, List<Candidate> candidates,
                             UUID myVoteTeamId, List<Result> results, long totalVotes) {
    }

    public record CastVoteRequest(@NotNull UUID teamId) {
    }
}
