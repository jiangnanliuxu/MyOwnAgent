CREATE TABLE user_auth_credentials (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_expires ON refresh_tokens(user_id, expires_at DESC);

CREATE TRIGGER trg_user_auth_credentials_updated_at
  BEFORE UPDATE ON user_auth_credentials
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_refresh_tokens_updated_at
  BEFORE UPDATE ON refresh_tokens
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
