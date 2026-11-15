package ie.itlimerick.summit.speaker;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ie.itlimerick.summit.common.Urls;

public final class SpeakerDtos {

    private SpeakerDtos() {
    }

    public record SpeakerView(UUID id, String name, String headline, String company, String bio, String photoUrl,
                              String linkedinUrl, String websiteUrl, boolean published, int sortOrder) {

        public static SpeakerView of(Speaker s) {
            return new SpeakerView(s.getId(), s.getName(), s.getHeadline(), s.getCompany(), s.getBio(), s.getPhotoUrl(),
                    s.getLinkedinUrl(), s.getWebsiteUrl(), s.isPublished(), s.getSortOrder());
        }
    }

    public record SpeakerSessionView(UUID id, String title, Instant startsAt, Instant endsAt, String roomName) {
    }

    public record SpeakerDetail(SpeakerView speaker, String eventSlug, List<SpeakerSessionView> sessions) {
    }

    public record SpeakerRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 200) String headline,
            @Size(max = 150) String company,
            @Size(max = 5000) String bio,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String photoUrl,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String linkedinUrl,
            @Size(max = 1000) @Pattern(regexp = Urls.OPTIONAL_HTTP_URL, message = Urls.MESSAGE) String websiteUrl,
            boolean published,
            int sortOrder) {
    }
}
