CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "user" (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE verification_code (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE project (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tech_stack VARCHAR(255),
    repo_path VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TYPE pipeline_status AS ENUM ('running', 'waiting_human', 'completed', 'failed');
CREATE TYPE stage_type AS ENUM ('requirements', 'planning', 'coding', 'testing');
CREATE TYPE stage_status AS ENUM (
    'pending', 'running', 'waiting_answer', 'waiting_choice',
    'waiting_approval', 'waiting_revision', 'completed', 'failed', 'skipped'
);
CREATE TYPE message_role AS ENUM ('user', 'assistant', 'system');
CREATE TYPE message_type AS ENUM ('text', 'question', 'choice_request', 'choice_response');
CREATE TYPE artifact_type AS ENUM ('prd', 'plan', 'code', 'test_result');

CREATE TABLE pipeline_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    requirement TEXT NOT NULL,
    status pipeline_status NOT NULL DEFAULT 'running',
    current_stage stage_type NOT NULL DEFAULT 'requirements',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE stage_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_run(id) ON DELETE CASCADE,
    stage_type stage_type NOT NULL,
    status stage_status NOT NULL DEFAULT 'pending',
    order_index INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_run_id UUID NOT NULL REFERENCES stage_run(id) ON DELETE CASCADE,
    role message_role NOT NULL,
    content TEXT NOT NULL,
    type message_type NOT NULL DEFAULT 'text',
    options JSONB,
    selected_option VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE artifact (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_run_id UUID NOT NULL REFERENCES stage_run(id) ON DELETE CASCADE,
    type artifact_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE code_file (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    artifact_id UUID NOT NULL REFERENCES artifact(id) ON DELETE CASCADE,
    path VARCHAR(500) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    language VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE agent_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_type stage_type UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    system_prompt TEXT NOT NULL,
    model VARCHAR(100) NOT NULL DEFAULT 'claude-opus-4-6',
    max_tokens INT NOT NULL DEFAULT 8192,
    max_retries INT NOT NULL DEFAULT 3,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_pipeline_run_project ON pipeline_run(project_id);
CREATE INDEX idx_stage_run_pipeline ON stage_run(pipeline_run_id);
CREATE INDEX idx_message_stage_run ON message(stage_run_id);
CREATE INDEX idx_artifact_stage_run ON artifact(stage_run_id);
CREATE INDEX idx_code_file_artifact ON code_file(artifact_id);
