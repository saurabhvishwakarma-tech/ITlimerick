-- Google / LinkedIn sign-in, QR check-in and session feedback.

-- A person can sign in with Google and/or LinkedIn as well as (or instead of) a password.
-- "subject" is the provider's permanent id for the person; we never store their tokens.
CREATE TABLE user_identity (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider    TEXT        NOT NULL CHECK (provider IN ('google', 'linkedin')),
    subject     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, subject),
    UNIQUE (user_id, provider)
);

-- Short-lived state for a sign-in that is in progress (between leaving for Google and coming back).
CREATE TABLE oauth_login_state (
    state_hash     TEXT PRIMARY KEY,
    provider       TEXT        NOT NULL,
    code_verifier  TEXT        NOT NULL,
    return_to      TEXT        NOT NULL,
    link_user_id   UUID        REFERENCES app_user (id) ON DELETE CASCADE,
    expires_at     TIMESTAMPTZ NOT NULL
);

-- One ticket per person per event. The QR code on the attendee's phone holds the code.
CREATE TABLE event_ticket (
    event_id         UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    user_id          UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code             TEXT        NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    checked_in_at    TIMESTAMPTZ,
    checked_in_by    UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    PRIMARY KEY (event_id, user_id)
);

-- Star rating (and optional comment) for a session. Organisers see it without names.
CREATE TABLE session_feedback (
    session_id  UUID        NOT NULL REFERENCES agenda_session (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     TEXT        CHECK (comment IS NULL OR length(comment) <= 1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, user_id)
);
