CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(40) NOT NULL,
    username VARCHAR(40) NOT NULL,
    username_key VARCHAR(40) NOT NULL,
    password_hash VARCHAR(220) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_key ON users (username_key);

CREATE TABLE IF NOT EXISTS user_games (
    user_id VARCHAR(40) NOT NULL,
    game VARCHAR(20) NOT NULL,
    CONSTRAINT pk_user_games PRIMARY KEY (user_id, game),
    CONSTRAINT fk_user_games_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS activities (
    id VARCHAR(48) NOT NULL,
    game VARCHAR(20) NOT NULL,
    name VARCHAR(300) NOT NULL,
    kind VARCHAR(80),
    description CHARACTER LARGE OBJECT,
    start_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE,
    cover VARCHAR(1000),
    rewards_json CHARACTER LARGE OBJECT,
    version VARCHAR(40),
    version_name VARCHAR(200),
    challenge_stage VARCHAR(40),
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    synced_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_activities PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_activities_game_end ON activities (game, end_at);
CREATE INDEX IF NOT EXISTS idx_activities_game_name ON activities (game, name);

CREATE TABLE IF NOT EXISTS game_banners (
    id VARCHAR(48) NOT NULL,
    game VARCHAR(20) NOT NULL,
    external_id VARCHAR(40),
    name VARCHAR(200),
    version VARCHAR(40),
    banner_type VARCHAR(80),
    items_json CHARACTER LARGE OBJECT,
    start_at TIMESTAMP WITH TIME ZONE,
    end_at TIMESTAMP WITH TIME ZONE,
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    synced_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_game_banners PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_banners_game_end ON game_banners (game, end_at);

CREATE TABLE IF NOT EXISTS game_codes (
    id VARCHAR(48) NOT NULL,
    game VARCHAR(20) NOT NULL,
    code_value VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    rewards_json CHARACTER LARGE OBJECT,
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    synced_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_game_codes PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_codes_game_active ON game_codes (game, active);

CREATE TABLE IF NOT EXISTS game_sync_states (
    game VARCHAR(20) NOT NULL,
    status VARCHAR(20),
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    activity_count INTEGER NOT NULL,
    version VARCHAR(40),
    CONSTRAINT pk_game_sync_states PRIMARY KEY (game)
);

CREATE TABLE IF NOT EXISTS user_activity_states (
    id VARCHAR(96) NOT NULL,
    user_id VARCHAR(40) NOT NULL,
    activity_id VARCHAR(48) NOT NULL,
    completed BOOLEAN NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_user_activity_states PRIMARY KEY (id),
    CONSTRAINT uk_user_activity UNIQUE (user_id, activity_id)
);

CREATE INDEX IF NOT EXISTS idx_states_user ON user_activity_states (user_id);
