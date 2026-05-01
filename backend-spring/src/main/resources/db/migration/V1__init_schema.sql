CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  avatar_url TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled', 'deleted')),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL DEFAULT '默认工作区',
  description TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived', 'deleted')),
  settings JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_configs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  active_thread_key TEXT,
  preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE folders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  path TEXT,
  sort_order INT NOT NULL DEFAULT 0,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, name)
);

CREATE TABLE roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  client_key TEXT,
  name TEXT NOT NULL,
  alias TEXT,
  tag TEXT,
  description TEXT,
  short_description TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived', 'disabled')),
  is_builtin BOOLEAN NOT NULL DEFAULT false,
  config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, client_key)
);

CREATE TABLE threads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
  client_key TEXT NOT NULL,
  label TEXT NOT NULL,
  summary TEXT,
  focus_role_id UUID REFERENCES roles(id) ON DELETE SET NULL,
  role_status TEXT NOT NULL DEFAULT '已编排' CHECK (role_status IN ('已编排', '未编排')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived', 'deleted')),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, client_key)
);

CREATE TABLE messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
  client_message_id TEXT,
  role TEXT NOT NULL CHECK (role IN ('user', 'agent', 'system', 'tool')),
  agent_name TEXT,
  agent_id UUID REFERENCES roles(id) ON DELETE SET NULL,
  content TEXT NOT NULL,
  kind TEXT NOT NULL DEFAULT 'text' CHECK (kind IN ('text', 'tool_call', 'tool_result', 'system', 'event')),
  status TEXT NOT NULL DEFAULT 'completed' CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'cancelled')),
  error_code TEXT,
  completed_at TIMESTAMPTZ,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (thread_id, client_message_id)
);

CREATE TABLE thread_roles (
  thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  is_focus BOOLEAN NOT NULL DEFAULT false,
  status TEXT NOT NULL DEFAULT '已编排' CHECK (status IN ('已编排', '未编排', '停用')),
  sort_order INT NOT NULL DEFAULT 0,
  mount_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (thread_id, role_id)
);

CREATE TABLE skills (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  client_key TEXT,
  name TEXT NOT NULL,
  source TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT '待启用' CHECK (status IN ('启用', '停用', '待启用')),
  scope TEXT,
  mounts TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  last_run_at TIMESTAMPTZ,
  manifest JSONB NOT NULL DEFAULT '{}'::jsonb,
  config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, client_key)
);

CREATE TABLE mcp_endpoints (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  client_key TEXT,
  name TEXT NOT NULL,
  transport TEXT NOT NULL CHECK (transport IN ('stdio', 'http', 'streamable_http', 'sse', 'plugin api', 'iab')),
  status TEXT NOT NULL DEFAULT '待连接' CHECK (status IN ('已连接', '待连接', '异常', '停用')),
  auth_type TEXT NOT NULL DEFAULT 'local',
  url TEXT,
  command JSONB NOT NULL DEFAULT '{}'::jsonb,
  tools TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  latency_ms INT,
  secret_ref TEXT,
  health_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, client_key)
);

CREATE TABLE mcp_health_checks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  endpoint_id UUID NOT NULL REFERENCES mcp_endpoints(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('正常', '异常', '待确认')),
  latency_ms INT,
  message TEXT,
  checked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rag_documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  thread_id UUID REFERENCES threads(id) ON DELETE SET NULL,
  folder_id UUID REFERENCES folders(id) ON DELETE SET NULL,
  uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source_name TEXT NOT NULL,
  source_path TEXT,
  storage_uri TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
  sha256 TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'uploaded' CHECK (status IN ('uploaded', 'indexing', 'indexed', 'failed', 'deleted')),
  chunk_count INT NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
  embedding_model TEXT,
  embedding_dimension INT,
  milvus_collection TEXT NOT NULL DEFAULT 'agent_desk_chunks',
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  error_message TEXT,
  indexed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rag_chunks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  document_id UUID NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
  thread_id UUID REFERENCES threads(id) ON DELETE SET NULL,
  chunk_index INT NOT NULL CHECK (chunk_index >= 0),
  content TEXT NOT NULL,
  token_count INT CHECK (token_count IS NULL OR token_count >= 0),
  page_start INT,
  page_end INT,
  content_hash TEXT NOT NULL,
  vector_id TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (document_id, chunk_index)
);

CREATE TABLE rag_index_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  document_id UUID NOT NULL REFERENCES rag_documents(id) ON DELETE CASCADE,
  thread_id UUID REFERENCES threads(id) ON DELETE SET NULL,
  job_type TEXT NOT NULL DEFAULT 'index' CHECK (job_type IN ('index', 'reindex', 'delete')),
  status TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
  error_message TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE task_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  thread_id UUID REFERENCES threads(id) ON DELETE SET NULL,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  type TEXT NOT NULL,
  level TEXT NOT NULL DEFAULT 'info' CHECK (level IN ('debug', 'info', 'warn', 'error')),
  message TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_user_status ON projects(user_id, status);
CREATE INDEX idx_folders_project_sort ON folders(project_id, sort_order);
CREATE INDEX idx_roles_project_status ON roles(project_id, status);
CREATE INDEX idx_threads_project_folder_status ON threads(project_id, folder_id, status);
CREATE INDEX idx_threads_focus_role ON threads(focus_role_id);
CREATE INDEX idx_messages_thread_created ON messages(thread_id, created_at);
CREATE INDEX idx_messages_project_status ON messages(project_id, status, created_at DESC);
CREATE UNIQUE INDEX idx_thread_roles_single_focus ON thread_roles(thread_id) WHERE is_focus;
CREATE INDEX idx_thread_roles_role ON thread_roles(role_id);
CREATE INDEX idx_skills_project_status ON skills(project_id, status);
CREATE INDEX idx_mcp_endpoints_project_status ON mcp_endpoints(project_id, status);
CREATE INDEX idx_mcp_health_endpoint_time ON mcp_health_checks(endpoint_id, checked_at DESC);
CREATE INDEX idx_rag_documents_project_thread_status ON rag_documents(project_id, thread_id, status);
CREATE INDEX idx_rag_documents_project_sha256 ON rag_documents(project_id, sha256);
CREATE INDEX idx_rag_chunks_document_index ON rag_chunks(document_id, chunk_index);
CREATE INDEX idx_rag_chunks_project_thread ON rag_chunks(project_id, thread_id);
CREATE INDEX idx_rag_index_jobs_status ON rag_index_jobs(status, created_at);
CREATE INDEX idx_task_logs_project_time ON task_logs(project_id, created_at DESC);
CREATE INDEX idx_task_logs_thread_time ON task_logs(thread_id, created_at DESC);

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_projects_updated_at BEFORE UPDATE ON projects FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_user_configs_updated_at BEFORE UPDATE ON user_configs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_folders_updated_at BEFORE UPDATE ON folders FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_threads_updated_at BEFORE UPDATE ON threads FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_messages_updated_at BEFORE UPDATE ON messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_thread_roles_updated_at BEFORE UPDATE ON thread_roles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_skills_updated_at BEFORE UPDATE ON skills FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_mcp_endpoints_updated_at BEFORE UPDATE ON mcp_endpoints FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_rag_documents_updated_at BEFORE UPDATE ON rag_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
