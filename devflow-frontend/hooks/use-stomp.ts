'use client';

import { useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAppStore } from '@/store/app-store';
import { StreamChunkEvent, CodeFileEvent, StageUpdateEvent, PipelineUpdateEvent } from '@/types';

const WS_URL = 'http://localhost:8080/ws';

export function useStomp(pipelineRunId: string | null, stageRunId: string | null) {
  const clientRef = useRef<Client | null>(null);
  const {
    appendStreamText,
    setCodeFiles,
    codeFiles,
    setCurrentStage,
    setCurrentPipeline,
    setMessages,
    appendMessage,
  } = useAppStore();

  const connect = useCallback(() => {
    if (clientRef.current?.active) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        if (pipelineRunId) {
          client.subscribe(`/topic/pipeline/${pipelineRunId}`, (msg) => {
            const event: PipelineUpdateEvent = JSON.parse(msg.body);
            setCurrentPipeline({
              ...useAppStore.getState().currentPipeline!,
              status: event.status,
            });
          });

          client.subscribe(`/topic/pipeline/${pipelineRunId}/stage`, (msg) => {
            const event: StageUpdateEvent = JSON.parse(msg.body);
            setCurrentStage({
              ...useAppStore.getState().currentStage!,
              status: event.status,
            });
          });
        }

        if (stageRunId) {
          client.subscribe(`/topic/stage/${stageRunId}/stream`, (msg) => {
            const event: StreamChunkEvent = JSON.parse(msg.body);
            appendStreamText(event.chunk);
          });

          client.subscribe(`/topic/stage/${stageRunId}/file`, (msg) => {
            const event: CodeFileEvent = JSON.parse(msg.body);
            const existing = codeFiles.find((f) => f.id === event.fileId);
            if (!existing) {
              setCodeFiles([
                ...codeFiles,
                { id: event.fileId, path: event.path, language: event.language, updatedAt: new Date().toISOString() },
              ]);
            }
          });
        }
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers.message);
      },
    });

    client.activate();
    clientRef.current = client;
  }, [pipelineRunId, stageRunId, appendStreamText, setCodeFiles, codeFiles, setCurrentStage, setCurrentPipeline]);

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
    clientRef.current = null;
  }, []);

  useEffect(() => {
    if (pipelineRunId || stageRunId) {
      connect();
    }
    return () => disconnect();
  }, [pipelineRunId, stageRunId, connect, disconnect]);

  return { connect, disconnect };
}
