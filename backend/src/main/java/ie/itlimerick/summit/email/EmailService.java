package ie.itlimerick.summit.email;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ie.itlimerick.summit.config.AppProperties;

/**
 * Sends transactional emails (currently: password reset links).
 *
 * If EMAIL_WEBHOOK_URL is set, each email is POSTed as JSON {"to","subject","text"} to that URL,
 * so the hosting company can connect any provider (AWS SES via a small Lambda, Postmark, etc.).
 * If it is not set, emails are only logged (without their contents) and organisers must reset
 * passwords another way. See docs/HANDOVER.md.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final AppProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public EmailService(AppProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        String url = props.email() == null ? null : props.email().webhookUrl();
        return url != null && !url.isBlank();
    }

    public void send(String to, String subject, String text) {
        if (props.email() != null && props.email().logContent()) {
            log.warn("TEST MODE email to {}\nSubject: {}\n{}", to, subject, text);
        }
        if (!isConfigured()) {
            log.warn("Email not sent (EMAIL_WEBHOOK_URL not configured): subject='{}'", subject);
            return;
        }
        String json = "{\"to\":" + quote(to) + ",\"subject\":" + quote(subject) + ",\"text\":" + quote(text) + "}";
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(props.email().webhookUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        String token = props.email().webhookToken();
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                log.error("Email webhook returned HTTP {} for subject='{}'", response.statusCode(), subject);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Email webhook failed for subject='{}': {}", subject, e.getMessage());
        }
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
