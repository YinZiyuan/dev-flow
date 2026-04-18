export interface User {
  email: string;
  name: string;
}

export interface AuthResult {
  token: string;
  user: User;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  techStack: string;
  repoPath: string;
  createdAt: string;
}

export interface CreateProjectRequest {
  name: string;
  description: string;
  techStack: string;
  repoPath: string;
}

export interface PipelineRun {
  id: string;
  requirement: string;
  status: PipelineStatus;
  currentStage: StageType;
  createdAt: string;
}

export type PipelineStatus = 'running' | 'waiting_human' | 'completed' | 'failed';
export type StageType = 'requirements' | 'planning' | 'coding' | 'testing';
export type StageStatus =
  | 'pending'
  | 'running'
  | 'waiting_answer'
  | 'waiting_choice'
  | 'waiting_approval'
  | 'waiting_revision'
  | 'completed'
  | 'failed'
  | 'skipped';

export interface StageRun {
  id: string;
  stageType: StageType;
  status: StageStatus;
  orderIndex: number;
  startedAt: string | null;
  completedAt: string | null;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  type: 'text' | 'question' | 'choice_request' | 'choice_response';
  options: string | null;
  selectedOption: string | null;
  createdAt: string;
}

export interface ChoiceOption {
  id: string;
  label: string;
  description: string;
}

export interface Artifact {
  id: string;
  type: 'prd' | 'plan' | 'code' | 'test_result';
  title: string;
  content: string;
  approvedAt: string | null;
  createdAt: string;
}

export interface CodeFile {
  id: string;
  path: string;
  language: string;
  content?: string;
  updatedAt: string;
}

export interface AgentConfig {
  id: string;
  stageType: StageType;
  name: string;
  systemPrompt: string;
  model: string;
  maxTokens: number;
  maxRetries: number;
  updatedAt: string;
}

export interface UpdateAgentConfigRequest {
  systemPrompt: string;
  model: string;
  maxTokens: number;
  maxRetries: number;
}

export interface CreatePipelineRequest {
  requirement: string;
}

export interface AnswerRequest {
  content: string;
}

export interface ChoiceRequest {
  optionId: string;
}

export interface RevisionRequest {
  feedback: string;
}

export interface UpdateFileRequest {
  content: string;
}

export interface PipelineUpdateEvent {
  pipelineRunId: string;
  status: PipelineStatus;
}

export interface StageUpdateEvent {
  stageRunId: string;
  status: StageStatus;
}

export interface StreamChunkEvent {
  chunk: string;
}

export interface CodeFileEvent {
  fileId: string;
  path: string;
  language: string;
}
