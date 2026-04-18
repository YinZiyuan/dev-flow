import axios, { AxiosInstance } from 'axios';
import {
  AuthResult,
  User,
  Project,
  CreateProjectRequest,
  PipelineRun,
  CreatePipelineRequest,
  StageRun,
  Message,
  Artifact,
  CodeFile,
  AgentConfig,
  UpdateAgentConfigRequest,
  AnswerRequest,
  ChoiceRequest,
  RevisionRequest,
  UpdateFileRequest,
} from '@/types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

function getToken(): string | null {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('devflow_token');
  }
  return null;
}

export const api: AxiosInstance = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 403) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('devflow_token');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  sendCode: (email: string) =>
    api.post('/api/auth/send-code', { email }),

  verifyCode: (email: string, code: string) =>
    api.post<AuthResult>('/api/auth/verify-code', { email, code }).then((r) => r.data),

  me: () => api.get<User>('/api/auth/me').then((r) => r.data),
};

export const projectApi = {
  list: () => api.get<Project[]>('/api/projects').then((r) => r.data),

  create: (data: CreateProjectRequest) =>
    api.post<Project>('/api/projects', data).then((r) => r.data),

  get: (id: string) => api.get<Project>(`/api/projects/${id}`).then((r) => r.data),

  update: (id: string, data: CreateProjectRequest) =>
    api.put<Project>(`/api/projects/${id}`, data).then((r) => r.data),

  delete: (id: string) => api.delete(`/api/projects/${id}`),
};

export const pipelineApi = {
  list: (projectId: string) =>
    api.get<PipelineRun[]>(`/api/projects/${projectId}/pipelines`).then((r) => r.data),

  create: (projectId: string, data: CreatePipelineRequest) =>
    api.post<PipelineRun>(`/api/projects/${projectId}/pipelines`, data).then((r) => r.data),

  get: (projectId: string, runId: string) =>
    api.get<PipelineRun>(`/api/projects/${projectId}/pipelines/${runId}`).then((r) => r.data),

  stages: (runId: string) =>
    api.get<StageRun[]>(`/api/projects/${runId}/stages`).then((r) => r.data),

  messages: (stageId: string) =>
    api.get<Message[]>(`/api/projects/_/pipelines/_/stages/${stageId}/messages`).then((r) => r.data),

  artifact: (stageId: string) =>
    api.get<Artifact>(`/api/projects/_/pipelines/_/stages/${stageId}/artifact`).then((r) => r.data),

  files: (stageId: string) =>
    api.get<CodeFile[]>(`/api/projects/_/pipelines/_/stages/${stageId}/files`).then((r) => r.data),
};

export const stageApi = {
  answer: (stageId: string, data: AnswerRequest) =>
    api.post(`/api/stages/${stageId}/answer`, data),

  choose: (stageId: string, data: ChoiceRequest) =>
    api.post(`/api/stages/${stageId}/choose`, data),

  approve: (stageId: string) =>
    api.post(`/api/stages/${stageId}/approve`),

  revise: (stageId: string, data: RevisionRequest) =>
    api.post(`/api/stages/${stageId}/revise`, data),

  updateFile: (fileId: string, data: UpdateFileRequest) =>
    api.patch(`/api/stages/files/${fileId}`, data),
};

export const agentConfigApi = {
  list: () => api.get<AgentConfig[]>('/api/agent-configs').then((r) => r.data),

  update: (id: string, data: UpdateAgentConfigRequest) =>
    api.put<AgentConfig>(`/api/agent-configs/${id}`, data).then((r) => r.data),
};
