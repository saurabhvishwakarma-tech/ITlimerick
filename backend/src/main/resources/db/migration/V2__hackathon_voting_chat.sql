-- Hackathon teams, voting and chat.

ALTER TABLE event
    ADD COLUMN hackathon_enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN team_max_size          INT         NOT NULL DEFAULT 5 CHECK (team_max_size BETWEEN 1 AND 50),
    ADD COLUMN voting_opens_at        TIMESTAMPTZ,
    ADD COLUMN voting_closes_at       TIMESTAMPTZ,
    ADD COLUMN voting_results_public  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN chat_enabled           BOOLEAN     NOT NULL DEFAULT FALSE;

-- People who signed up for the hackathon. "Looking for a team" puts them in the pool the team sorter uses.
CREATE TABLE hackathon_participant (
    event_id          UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    skills            TEXT,
    looking_for_team  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, user_id)
);

-- A team and its project (TechIreland calls these "Workspace" projects).
CREATE TABLE team (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    name          TEXT        NOT NULL,
    project_name  TEXT,
    description   TEXT,
    sector        TEXT,
    logo_url      TEXT,
    project_url   TEXT,
    shortlisted   BOOLEAN     NOT NULL DEFAULT FALSE,
    join_code     TEXT        NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_team_event ON team (event_id);

-- One team per person per event (event_id is repeated here so the database can enforce it).
CREATE TABLE team_member (
    team_id    UUID        NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    event_id   UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    role       TEXT        NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('LEAD', 'MEMBER')),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (team_id, user_id),
    UNIQUE (event_id, user_id)
);

-- One vote per person per event. People can change their vote until voting closes.
CREATE TABLE vote (
    event_id   UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    team_id    UUID        NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    voted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, user_id)
);
CREATE INDEX ix_vote_team ON vote (team_id);

CREATE TABLE announcement_reaction (
    announcement_id  UUID        NOT NULL REFERENCES announcement (id) ON DELETE CASCADE,
    user_id          UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    emoji            TEXT        NOT NULL CHECK (emoji IN ('HEART', 'THUMBS_UP', 'PARTY', 'CLAP')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (announcement_id, user_id, emoji)
);

-- Attendee chat (off by default per event). Organisers can remove messages.
CREATE TABLE chat_message (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    user_id     UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    deleted_by  UUID        REFERENCES app_user (id) ON DELETE SET NULL
);
CREATE INDEX ix_chat_event_created ON chat_message (event_id, created_at);
