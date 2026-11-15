package ie.itlimerick.summit.common;

/** Shared validation pattern for optional web links (empty, or http/https). */
public final class Urls {

    public static final String OPTIONAL_HTTP_URL = "^$|^https?://\\S+$";
    public static final String MESSAGE = "Must be a full link starting with https://";

    private Urls() {
    }
}
