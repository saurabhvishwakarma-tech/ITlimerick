package ie.itlimerick.summit.hackathon;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.account.AppUser;
import ie.itlimerick.summit.account.AppUserRepository;
import ie.itlimerick.summit.common.ApiException;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventService;
import ie.itlimerick.summit.hackathon.HackathonDtos.AdminTeamRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.AutoSortResult;
import ie.itlimerick.summit.hackathon.HackathonDtos.MemberView;
import ie.itlimerick.summit.hackathon.HackathonDtos.MyHackathonView;
import ie.itlimerick.summit.hackathon.HackathonDtos.ParticipantView;
import ie.itlimerick.summit.hackathon.HackathonDtos.TeamAdminView;
import ie.itlimerick.summit.hackathon.HackathonDtos.TeamRequest;
import ie.itlimerick.summit.hackathon.HackathonDtos.TeamView;
import ie.itlimerick.summit.security.CurrentUser;

import static ie.itlimerick.summit.event.EventService.blankToNull;

/**
 * Hackathon participants and teams.
 *
 * People sign up as participants, then either create a team (and share its join code), join one
 * with a code, or tick "looking for a team" and let the organisers' team sorter place them.
 */
@Service
public class HackathonService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final HackathonParticipantRepository participants;
    private final AppUserRepository users;
    private final EventService events;

    public HackathonService(TeamRepository teams, TeamMemberRepository members,
                            HackathonParticipantRepository participants, AppUserRepository users, EventService events) {
        this.teams = teams;
        this.members = members;
        this.participants = participants;
        this.users = users;
        this.events = events;
    }

    /** A published event with the hackathon switched on; otherwise 404. */
    @Transactional(readOnly = true)
    public Event requireHackathon(String slug) {
        Event event = events.requirePublished(slug);
        if (!event.isHackathonEnabled()) {
            throw ApiException.notFound("Hackathon");
        }
        return event;
    }

    // ---------------- Public ----------------

    @Transactional(readOnly = true)
    public List<TeamView> listTeams(String slug, boolean showNames) {
        Event event = requireHackathon(slug);
        return teamViews(event.getId(), teams.findByEventIdOrderByNameAsc(event.getId()), showNames);
    }

    @Transactional(readOnly = true)
    public TeamView team(UUID teamId, boolean showNames) {
        Team team = teams.findById(teamId).orElseThrow(() -> ApiException.notFound("Team"));
        Event event = events.require(team.getEventId());
        if (!event.isPublished() || !event.isHackathonEnabled()) {
            throw ApiException.notFound("Team");
        }
        return teamViews(event.getId(), List.of(team), showNames).getFirst();
    }

    // ---------------- Me ----------------

    @Transactional(readOnly = true)
    public MyHackathonView myStatus(String slug, CurrentUser user) {
        Event event = requireHackathon(slug);
        Optional<HackathonParticipant> p = participants.findById(new HackathonParticipant.Key(event.getId(), user.id()));
        Optional<TeamMember> membership = members.findByEventIdAndUserId(event.getId(), user.id());
        TeamView team = null;
        String code = null;
        TeamMember.Role role = null;
        if (membership.isPresent()) {
            Team t = teams.findById(membership.get().getTeamId()).orElseThrow();
            team = teamViews(event.getId(), List.of(t), true).getFirst();
            code = t.getJoinCode();
            role = membership.get().getRole();
        }
        return new MyHackathonView(p.isPresent() || membership.isPresent(), p.map(HackathonParticipant::getSkills).orElse(null),
                p.map(HackathonParticipant::isLookingForTeam).orElse(false) && membership.isEmpty(),
                team, role, code, event.getTeamMaxSize());
    }

    @Transactional
    public MyHackathonView joinPool(String slug, CurrentUser user, String skills, boolean lookingForTeam) {
        Event event = requireHackathon(slug);
        HackathonParticipant p = participants.findById(new HackathonParticipant.Key(event.getId(), user.id()))
                .orElseGet(() -> new HackathonParticipant(event.getId(), user.id()));
        p.setSkills(blankToNull(skills));
        p.setLookingForTeam(lookingForTeam && members.findByEventIdAndUserId(event.getId(), user.id()).isEmpty());
        participants.save(p);
        return myStatus(slug, user);
    }

    /** Leaves the hackathon completely: off the participant list and out of any team. */
    @Transactional
    public void leaveHackathon(String slug, CurrentUser user) {
        Event event = requireHackathon(slug);
        removeFromTeam(event.getId(), user.id());
        participants.deleteById(new HackathonParticipant.Key(event.getId(), user.id()));
    }

    @Transactional
    public MyHackathonView createTeam(String slug, CurrentUser user, TeamRequest req) {
        Event event = requireHackathon(slug);
        if (members.findByEventIdAndUserId(event.getId(), user.id()).isPresent()) {
            throw ApiException.conflict("You're already in a team. Leave it first to start a new one.");
        }
        Team team = new Team();
        team.setEventId(event.getId());
        applyTeam(team, req.name(), req.projectName(), req.description(), req.sector(), req.logoUrl(), req.projectUrl());
        team.setJoinCode(newJoinCode());
        team = teams.save(team);
        members.save(new TeamMember(team.getId(), user.id(), event.getId(), TeamMember.Role.LEAD));
        ensureParticipant(event.getId(), user.id(), false);
        return myStatus(slug, user);
    }

    @Transactional
    public MyHackathonView joinTeam(CurrentUser user, String rawCode) {
        String code = rawCode.trim().toUpperCase();
        Team team = teams.findByJoinCode(code).orElseThrow(() -> ApiException.badRequest("No team uses that code. Check it with your team."));
        Event event = events.require(team.getEventId());
        if (!event.isPublished() || !event.isHackathonEnabled()) {
            throw ApiException.badRequest("No team uses that code. Check it with your team.");
        }
        if (members.findByEventIdAndUserId(event.getId(), user.id()).isPresent()) {
            throw ApiException.conflict("You're already in a team. Leave it first to join another.");
        }
        if (members.countByTeamId(team.getId()) >= event.getTeamMaxSize()) {
            throw ApiException.conflict("That team is full (" + event.getTeamMaxSize() + " people).");
        }
        members.save(new TeamMember(team.getId(), user.id(), event.getId(), TeamMember.Role.MEMBER));
        ensureParticipant(event.getId(), user.id(), false);
        return myStatus(event.getSlug(), user);
    }

    @Transactional
    public MyHackathonView leaveTeam(String slug, CurrentUser user) {
        Event event = requireHackathon(slug);
        removeFromTeam(event.getId(), user.id());
        return myStatus(slug, user);
    }

    /** Team members can edit their own project details; only organisers can shortlist. */
    @Transactional
    public MyHackathonView updateMyTeam(String slug, CurrentUser user, TeamRequest req) {
        Event event = requireHackathon(slug);
        TeamMember m = members.findByEventIdAndUserId(event.getId(), user.id())
                .orElseThrow(() -> ApiException.badRequest("You're not in a team yet"));
        Team team = teams.findById(m.getTeamId()).orElseThrow();
        applyTeam(team, req.name(), req.projectName(), req.description(), req.sector(), req.logoUrl(), req.projectUrl());
        return myStatus(slug, user);
    }

    // ---------------- Organisers ----------------

    @Transactional(readOnly = true)
    public List<TeamAdminView> adminTeams(UUID eventId) {
        events.require(eventId);
        List<Team> list = teams.findByEventIdOrderByNameAsc(eventId);
        Map<UUID, List<TeamMember>> byTeam = members.findByEventId(eventId).stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId));
        Map<UUID, AppUser> userById = usersById(members.findByEventId(eventId).stream().map(TeamMember::getUserId).toList());
        return list.stream().map(t -> toAdminView(t, byTeam.getOrDefault(t.getId(), List.of()), userById)).toList();
    }

    @Transactional
    public TeamAdminView adminCreateTeam(UUID eventId, AdminTeamRequest req) {
        events.require(eventId);
        Team team = new Team();
        team.setEventId(eventId);
        applyTeam(team, req.name(), req.projectName(), req.description(), req.sector(), req.logoUrl(), req.projectUrl());
        team.setShortlisted(req.shortlisted());
        team.setJoinCode(newJoinCode());
        team = teams.save(team);
        return toAdminView(team, List.of(), Map.of());
    }

    @Transactional
    public TeamAdminView adminUpdateTeam(UUID eventId, UUID teamId, AdminTeamRequest req) {
        Team team = requireTeam(eventId, teamId);
        applyTeam(team, req.name(), req.projectName(), req.description(), req.sector(), req.logoUrl(), req.projectUrl());
        team.setShortlisted(req.shortlisted());
        return adminTeams(eventId).stream().filter(t -> t.id().equals(teamId)).findFirst().orElseThrow();
    }

    @Transactional
    public void adminDeleteTeam(UUID eventId, UUID teamId) {
        Team team = requireTeam(eventId, teamId);
        // Members go back into the pool so the sorter can place them again.
        members.findByTeamIdOrderByJoinedAtAsc(teamId).forEach(m -> ensureParticipant(eventId, m.getUserId(), true));
        teams.delete(team);
    }

    /** Puts a participant into a team, moving them out of any other team. Organisers may exceed the size limit. */
    @Transactional
    public TeamAdminView adminAddMember(UUID eventId, UUID teamId, UUID userId) {
        Team team = requireTeam(eventId, teamId);
        if (!users.existsById(userId)) {
            throw ApiException.notFound("User");
        }
        Optional<TeamMember> current = members.findByEventIdAndUserId(eventId, userId);
        if (current.isPresent() && current.get().getTeamId().equals(teamId)) {
            return adminTeams(eventId).stream().filter(t -> t.id().equals(teamId)).findFirst().orElseThrow();
        }
        current.ifPresent(m -> removeFromTeam(eventId, userId));
        members.flush();
        boolean hasLead = members.findByTeamIdOrderByJoinedAtAsc(team.getId()).stream()
                .anyMatch(m -> m.getRole() == TeamMember.Role.LEAD);
        members.save(new TeamMember(team.getId(), userId, eventId, hasLead ? TeamMember.Role.MEMBER : TeamMember.Role.LEAD));
        ensureParticipant(eventId, userId, false);
        return adminTeams(eventId).stream().filter(t -> t.id().equals(teamId)).findFirst().orElseThrow();
    }

    @Transactional
    public void adminRemoveMember(UUID eventId, UUID teamId, UUID userId) {
        requireTeam(eventId, teamId);
        TeamMember m = members.findById(new TeamMember.Key(teamId, userId)).orElseThrow(() -> ApiException.notFound("Member"));
        removeFromTeam(eventId, m.getUserId());
        ensureParticipant(eventId, userId, true);
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> adminParticipants(UUID eventId) {
        events.require(eventId);
        List<HackathonParticipant> list = participants.findByEventIdOrderByCreatedAtAsc(eventId);
        Map<UUID, TeamMember> memberByUser = members.findByEventId(eventId).stream()
                .collect(Collectors.toMap(TeamMember::getUserId, Function.identity()));
        Map<UUID, Team> teamById = teams.findByEventIdOrderByNameAsc(eventId).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<UUID, AppUser> userById = usersById(list.stream().map(HackathonParticipant::getUserId).toList());
        return list.stream().map(p -> {
            AppUser u = userById.get(p.getUserId());
            TeamMember m = memberByUser.get(p.getUserId());
            Team t = m == null ? null : teamById.get(m.getTeamId());
            return new ParticipantView(p.getUserId(), u == null ? "" : u.getDisplayName(), u == null ? "" : u.getEmail(),
                    p.getSkills(), p.isLookingForTeam() && m == null, t == null ? null : t.getId(),
                    t == null ? null : t.getName(), p.getCreatedAt());
        }).toList();
    }

    /**
     * The team sorter. Takes everyone who is "looking for a team" and not yet in one, shuffles them,
     * tops up existing teams that have space (smallest first), then creates new teams for the rest
     * so that new teams end up as even in size as possible.
     */
    @Transactional
    public AutoSortResult autoSort(UUID eventId, Integer requestedSize) {
        Event event = events.require(eventId);
        int max = requestedSize != null ? requestedSize : event.getTeamMaxSize();
        if (max < 2) {
            throw ApiException.badRequest("Teams need room for at least 2 people");
        }
        Map<UUID, TeamMember> memberByUser = members.findByEventId(eventId).stream()
                .collect(Collectors.toMap(TeamMember::getUserId, Function.identity()));
        List<HackathonParticipant> pool = new ArrayList<>(participants.findByEventIdOrderByCreatedAtAsc(eventId).stream()
                .filter(p -> p.isLookingForTeam() && !memberByUser.containsKey(p.getUserId()))
                .toList());
        if (pool.isEmpty()) {
            return new HackathonDtos.AutoSortResult(0, 0, 0);
        }
        Collections.shuffle(pool, RANDOM);

        // 1. Top up existing teams that still have space.
        Map<UUID, Integer> sizes = new HashMap<>();
        teams.findByEventIdOrderByNameAsc(eventId).forEach(t -> sizes.put(t.getId(), 0));
        memberByUser.values().forEach(m -> sizes.merge(m.getTeamId(), 1, Integer::sum));
        int assigned = 0;
        java.util.Set<UUID> filled = new java.util.HashSet<>();
        List<Map.Entry<UUID, Integer>> open = new ArrayList<>(sizes.entrySet().stream().filter(e -> e.getValue() < max).toList());
        while (!pool.isEmpty() && !open.isEmpty()) {
            open.sort(Comparator.comparingInt(Map.Entry::getValue));
            Map.Entry<UUID, Integer> smallest = open.getFirst();
            HackathonParticipant p = pool.removeFirst();
            members.save(new TeamMember(smallest.getKey(), p.getUserId(), eventId,
                    smallest.getValue() == 0 ? TeamMember.Role.LEAD : TeamMember.Role.MEMBER));
            p.setLookingForTeam(false);
            smallest.setValue(smallest.getValue() + 1);
            filled.add(smallest.getKey());
            assigned++;
            if (smallest.getValue() >= max) {
                open.removeFirst();
            }
        }

        // 2. New teams for everyone left, as evenly sized as possible.
        int created = 0;
        if (!pool.isEmpty()) {
            int newTeams = (pool.size() + max - 1) / max;
            long existing = teams.countByEventId(eventId);
            List<Team> made = new ArrayList<>();
            for (int i = 0; i < newTeams; i++) {
                Team t = new Team();
                t.setEventId(eventId);
                t.setName("Team " + (existing + i + 1));
                t.setJoinCode(newJoinCode());
                made.add(teams.save(t));
            }
            for (int i = 0; i < pool.size(); i++) {
                HackathonParticipant p = pool.get(i);
                Team t = made.get(i % newTeams);
                members.save(new TeamMember(t.getId(), p.getUserId(), eventId,
                        i < newTeams ? TeamMember.Role.LEAD : TeamMember.Role.MEMBER));
                p.setLookingForTeam(false);
                assigned++;
            }
            created = newTeams;
        }
        return new AutoSortResult(assigned, created, filled.size());
    }

    // ---------------- Helpers ----------------

    Team requireTeam(UUID eventId, UUID teamId) {
        return teams.findById(teamId).filter(t -> t.getEventId().equals(eventId))
                .orElseThrow(() -> ApiException.notFound("Team"));
    }

    /** Removes someone from their team; if they led it, the longest-standing member becomes lead. */
    private void removeFromTeam(UUID eventId, UUID userId) {
        members.findByEventIdAndUserId(eventId, userId).ifPresent(m -> {
            members.delete(m);
            members.flush();
            if (m.getRole() == TeamMember.Role.LEAD) {
                members.findByTeamIdOrderByJoinedAtAsc(m.getTeamId()).stream().findFirst()
                        .ifPresent(next -> next.setRole(TeamMember.Role.LEAD));
            }
        });
    }

    private void ensureParticipant(UUID eventId, UUID userId, boolean lookingForTeam) {
        HackathonParticipant p = participants.findById(new HackathonParticipant.Key(eventId, userId))
                .orElseGet(() -> new HackathonParticipant(eventId, userId));
        p.setLookingForTeam(lookingForTeam);
        participants.save(p);
    }

    private void applyTeam(Team t, String name, String projectName, String description, String sector,
                           String logoUrl, String projectUrl) {
        t.setName(name.trim());
        t.setProjectName(blankToNull(projectName));
        t.setDescription(blankToNull(description));
        t.setSector(blankToNull(sector));
        t.setLogoUrl(blankToNull(logoUrl));
        t.setProjectUrl(blankToNull(projectUrl));
    }

    private String newJoinCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!teams.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique team code");
    }

    private Map<UUID, AppUser> usersById(List<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    List<TeamView> teamViews(UUID eventId, List<Team> list, boolean showNames) {
        Map<UUID, List<TeamMember>> byTeam = members.findByEventId(eventId).stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId));
        Map<UUID, AppUser> userById = showNames
                ? usersById(byTeam.values().stream().flatMap(List::stream).map(TeamMember::getUserId).toList())
                : Map.of();
        return list.stream().map(t -> {
            List<TeamMember> ms = byTeam.getOrDefault(t.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(TeamMember::getJoinedAt)).toList();
            List<String> names = showNames
                    ? ms.stream().map(m -> userById.get(m.getUserId())).filter(java.util.Objects::nonNull)
                    .map(AppUser::getDisplayName).toList()
                    : List.of();
            return new TeamView(t.getId(), t.getName(), t.getProjectName(), t.getDescription(), t.getSector(),
                    t.getLogoUrl(), t.getProjectUrl(), t.isShortlisted(), ms.size(), names);
        }).toList();
    }

    private TeamAdminView toAdminView(Team t, List<TeamMember> ms, Map<UUID, AppUser> userById) {
        List<MemberView> mv = ms.stream().sorted(Comparator.comparing(TeamMember::getJoinedAt)).map(m -> {
            AppUser u = userById.get(m.getUserId());
            return new MemberView(m.getUserId(), u == null ? "" : u.getDisplayName(), u == null ? "" : u.getEmail(), m.getRole());
        }).toList();
        return new TeamAdminView(t.getId(), t.getName(), t.getProjectName(), t.getDescription(), t.getSector(),
                t.getLogoUrl(), t.getProjectUrl(), t.isShortlisted(), t.getJoinCode(), mv);
    }

    Instant now() {
        return Instant.now();
    }
}
