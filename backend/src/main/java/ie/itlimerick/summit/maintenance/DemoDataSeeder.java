package ie.itlimerick.summit.maintenance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ie.itlimerick.summit.agenda.AgendaSession;
import ie.itlimerick.summit.agenda.AgendaSessionRepository;
import ie.itlimerick.summit.announcement.Announcement;
import ie.itlimerick.summit.announcement.AnnouncementRepository;
import ie.itlimerick.summit.event.Event;
import ie.itlimerick.summit.event.EventRepository;
import ie.itlimerick.summit.hackathon.Team;
import ie.itlimerick.summit.hackathon.TeamRepository;
import ie.itlimerick.summit.speaker.Speaker;
import ie.itlimerick.summit.speaker.SpeakerRepository;
import ie.itlimerick.summit.sponsor.Sponsor;
import ie.itlimerick.summit.sponsor.SponsorRepository;

/**
 * Fills an EMPTY database with a clearly-labelled sample event, so organisers can see how the app
 * works before real content exists. Only runs when SEED_DEMO_DATA=true. Never use in production.
 * All people and companies here are fictional.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");

    private final EventRepository events;
    private final SpeakerRepository speakers;
    private final AgendaSessionRepository sessions;
    private final SponsorRepository sponsors;
    private final AnnouncementRepository announcements;
    private final TeamRepository teams;
    private final String date;

    public DemoDataSeeder(EventRepository events, SpeakerRepository speakers, AgendaSessionRepository sessions,
                          SponsorRepository sponsors, AnnouncementRepository announcements, TeamRepository teams,
                          @Value("${app.seed-demo-date:2027-02-18}") String date) {
        this.teams = teams;
        this.events = events;
        this.speakers = speakers;
        this.sessions = sessions;
        this.sponsors = sponsors;
        this.announcements = announcements;
        this.date = date;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (events.count() > 0) {
            return;
        }
        LocalDate day = "today".equalsIgnoreCase(date) ? LocalDate.now(DUBLIN) : LocalDate.parse(date);
        log.warn("Seeding SAMPLE data for {} (app.seed-demo-data=true). Do not enable this in production.", day);

        Event e = new Event();
        e.setSlug("sample-summit");
        e.setName("IT Limerick Summit (sample)");
        e.setTagline("Sample content to show how the app works. Replace it in the admin panel.");
        e.setVenueName("Sample venue, Limerick");
        e.setStartsAt(at(day, "08:30"));
        e.setEndsAt(at(day, "17:00"));
        e.setStatus(Event.Status.PUBLISHED);
        e.setHackathonEnabled(true);
        e.setTeamMaxSize(4);
        e.setVotingOpensAt(at(day, "09:00"));
        e.setVotingClosesAt(at(day, "16:30"));
        e.setVotingResultsPublic(true);
        e.setChatEnabled(true);
        e = events.save(e);
        UUID id = e.getId();

        Speaker a = speaker(id, "Aoife Example", "Head of Engineering", "Sample Software Ltd", 0);
        Speaker b = speaker(id, "Ciarán Placeholder", "Data Scientist", "Example Analytics", 1);
        Speaker c = speaker(id, "Niamh Demo", "Founder", "Demo Startup", 2);
        Speaker d = speaker(id, "Seán Sample", "Cloud Architect", "Placeholder Cloud", 3);

        session(id, "Registration and coffee", AgendaSession.Type.NETWORKING, day, "08:30", "09:30", List.of());
        session(id, "Welcome and opening keynote", AgendaSession.Type.KEYNOTE, day, "09:30", "10:15", List.of(a.getId()));
        session(id, "Practical AI in small teams", AgendaSession.Type.TALK, day, "10:15", "11:00", List.of(b.getId()));
        session(id, "Coffee break", AgendaSession.Type.BREAK, day, "11:00", "11:30", List.of());
        session(id, "Panel: growing the Mid-West tech scene", AgendaSession.Type.PANEL, day, "11:30", "12:30",
                List.of(a.getId(), c.getId(), d.getId()));
        session(id, "Lunch and networking", AgendaSession.Type.BREAK, day, "12:30", "13:30", List.of());
        session(id, "Workshop: deploying your first cloud app", AgendaSession.Type.WORKSHOP, day, "13:30", "15:00",
                List.of(d.getId()));
        session(id, "From side project to startup", AgendaSession.Type.TALK, day, "15:15", "16:00", List.of(c.getId()));
        session(id, "Closing remarks", AgendaSession.Type.TALK, day, "16:30", "17:00", List.of(a.getId()));

        sponsor(id, "Sample Software Ltd", "Lead partner", 1, "Example description of a lead partner.");
        sponsor(id, "Example Analytics", "Partner", 2, null);
        sponsor(id, "Placeholder Cloud", "Partner", 2, null);
        sponsor(id, "Limerick Community Example", "Community partner", 3, null);

        team(id, "Shannon Coders", "FloodWatch", "Early warnings for river flooding using open sensor data.", "Climate", "SHNN01");
        team(id, "Treaty Labs", "ClinicQueue", "Shorter waits at GP clinics by predicting busy times.", "Health", "TRTY02");
        team(id, "Mid-West Makers", "SkillSwap", "Matches students with local mentors for short projects.", "Education", "MWMK03");
        team(id, "King John's Devs", "BinDay", "Reminds households which bin goes out, with recycling tips.", "Community", "KJDV04");

        Announcement welcome = new Announcement();
        welcome.setEventId(id);
        welcome.setTitle("Welcome to the sample summit");
        welcome.setBody("This is a sample update. Organisers can post room changes and reminders from the admin panel.");
        welcome.setPublishedAt(Instant.now().minusSeconds(3600));
        announcements.save(welcome);
    }

    private void team(UUID eventId, String name, String project, String description, String sector, String code) {
        Team t = new Team();
        t.setEventId(eventId);
        t.setName(name);
        t.setProjectName(project);
        t.setDescription(description + " (Fictional sample team.)");
        t.setSector(sector);
        t.setJoinCode(code);
        teams.save(t);
    }

    private static Instant at(LocalDate day, String time) {
        return LocalDateTime.of(day, LocalTime.parse(time)).atZone(DUBLIN).toInstant();
    }

    private Speaker speaker(UUID eventId, String name, String headline, String company, int order) {
        Speaker s = new Speaker();
        s.setEventId(eventId);
        s.setName(name);
        s.setHeadline(headline);
        s.setCompany(company);
        s.setBio(name + " is a fictional sample speaker. Replace this profile with a real one in the admin panel.");
        s.setPublished(true);
        s.setSortOrder(order);
        return speakers.save(s);
    }

    private void session(UUID eventId, String title, AgendaSession.Type type, LocalDate day, String start, String end,
                         List<UUID> speakerIds) {
        AgendaSession s = new AgendaSession();
        s.setEventId(eventId);
        s.setTitle(title);
        s.setType(type);
        s.setStartsAt(at(day, start));
        s.setEndsAt(at(day, end));
        s.getSpeakerIds().addAll(speakerIds);
        sessions.save(s);
    }

    private void sponsor(UUID eventId, String name, String tier, int rank, String description) {
        Sponsor s = new Sponsor();
        s.setEventId(eventId);
        s.setName(name);
        s.setTier(tier);
        s.setTierRank(rank);
        s.setDescription(description);
        sponsors.save(s);
    }
}
