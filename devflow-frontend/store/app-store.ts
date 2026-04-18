import { create } from 'zustand';
import { User, Project, PipelineRun, StageRun, Message, CodeFile, Artifact } from '@/types';

interface AppState {
  user: User | null;
  setUser: (user: User | null) => void;

  token: string | null;
  setToken: (token: string | null) => void;

  currentProject: Project | null;
  setCurrentProject: (project: Project | null) => void;

  currentPipeline: PipelineRun | null;
  setCurrentPipeline: (pipeline: PipelineRun | null) => void;

  currentStage: StageRun | null;
  setCurrentStage: (stage: StageRun | null) => void;

  messages: Message[];
  setMessages: (messages: Message[]) => void;
  appendMessage: (message: Message) => void;
  updateMessageContent: (index: number, content: string) => void;

  codeFiles: CodeFile[];
  setCodeFiles: (files: CodeFile[]) => void;
  updateCodeFile: (fileId: string, content: string) => void;

  selectedFile: CodeFile | null;
  setSelectedFile: (file: CodeFile | null) => void;

  artifact: Artifact | null;
  setArtifact: (artifact: Artifact | null) => void;

  streamText: string;
  setStreamText: (text: string) => void;
  appendStreamText: (chunk: string) => void;
  clearStreamText: () => void;
}

export const useAppStore = create<AppState>((set) => ({
  user: null,
  setUser: (user) => set({ user }),

  token: null,
  setToken: (token) => {
    if (token) {
      localStorage.setItem('devflow_token', token);
    } else {
      localStorage.removeItem('devflow_token');
    }
    set({ token });
  },

  currentProject: null,
  setCurrentProject: (project) => set({ currentProject: project }),

  currentPipeline: null,
  setCurrentPipeline: (pipeline) => set({ currentPipeline: pipeline }),

  currentStage: null,
  setCurrentStage: (stage) => set({ currentStage: stage }),

  messages: [],
  setMessages: (messages) => set({ messages }),
  appendMessage: (message) =>
    set((state) => ({ messages: [...state.messages, message] })),
  updateMessageContent: (index, content) =>
    set((state) => {
      const messages = [...state.messages];
      if (messages[index]) {
        messages[index] = { ...messages[index], content };
      }
      return { messages };
    }),

  codeFiles: [],
  setCodeFiles: (files) => set({ codeFiles: files }),
  updateCodeFile: (fileId, content) =>
    set((state) => ({
      codeFiles: state.codeFiles.map((f) =>
        f.id === fileId ? { ...f, content } : f
      ),
    })),

  selectedFile: null,
  setSelectedFile: (file) => set({ selectedFile: file }),

  artifact: null,
  setArtifact: (artifact) => set({ artifact }),

  streamText: '',
  setStreamText: (text) => set({ streamText: text }),
  appendStreamText: (chunk) =>
    set((state) => ({ streamText: state.streamText + chunk })),
  clearStreamText: () => set({ streamText: '' }),
}));
