'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { pipelineApi } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { Navbar } from '@/components/layout/navbar';
import { StageSidebar } from '@/components/pipeline/stage-sidebar';
import { MessageStream } from '@/components/pipeline/message-stream';
import { ActionBar } from '@/components/pipeline/action-bar';
import { useStomp } from '@/hooks/use-stomp';
import { useAppStore } from '@/store/app-store';
import { StageRun } from '@/types';

export default function PipelineWorkbenchPage() {
  useAuth();
  const params = useParams();
  const runId = params.id as string;

  const {
    currentPipeline,
    setCurrentPipeline,
    currentStage,
    setCurrentStage,
    messages,
    setMessages,
    codeFiles,
    setCodeFiles,
    clearStreamText,
  } = useAppStore();

  const { data: pipeline } = useQuery({
    queryKey: ['pipeline', runId],
    queryFn: () => pipelineApi.get('', runId),
  });

  const { data: stages } = useQuery({
    queryKey: ['stages', runId],
    queryFn: () => pipelineApi.stages(runId),
    refetchInterval: 3000,
  });

  const activeStage = currentStage || stages?.find((s) => s.status !== 'completed' && s.status !== 'skipped') || stages?.[stages.length - 1];

  const { data: stageMessages } = useQuery({
    queryKey: ['messages', activeStage?.id],
    queryFn: () => pipelineApi.messages(activeStage!.id),
    enabled: !!activeStage,
    refetchInterval: (query) => {
      const status = query.state.data?.length ? 'done' : 'loading';
      return activeStage?.status === 'running' ? 2000 : 5000;
    },
  });

  const { data: stageArtifact } = useQuery({
    queryKey: ['artifact', activeStage?.id],
    queryFn: () => pipelineApi.artifact(activeStage!.id),
    enabled: !!activeStage,
  });

  const { data: stageFiles } = useQuery({
    queryKey: ['files', activeStage?.id],
    queryFn: () => pipelineApi.files(activeStage!.id),
    enabled: !!activeStage,
  });

  useEffect(() => {
    if (pipeline) setCurrentPipeline(pipeline);
  }, [pipeline, setCurrentPipeline]);

  useEffect(() => {
    if (stages?.length) {
      const active = stages.find((s) => s.status !== 'completed' && s.status !== 'skipped') || stages[stages.length - 1];
      if (active && active.id !== currentStage?.id) {
        setCurrentStage(active);
        clearStreamText();
      }
    }
  }, [stages, currentStage, setCurrentStage, clearStreamText]);

  useEffect(() => {
    if (stageMessages) setMessages(stageMessages);
  }, [stageMessages, setMessages]);

  useEffect(() => {
    if (stageFiles) setCodeFiles(stageFiles);
  }, [stageFiles, setCodeFiles]);

  useStomp(runId, activeStage?.id || null);

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1 flex overflow-hidden">
        {stages && (
          <StageSidebar
            stages={stages}
            currentStageId={activeStage?.id || null}
            onSelectStage={(stage) => {
              setCurrentStage(stage);
              clearStreamText();
            }}
          />
        )}
        <div className="flex-1 flex flex-col min-w-0">
          <div className="border-b px-4 py-2 bg-background">
            <h2 className="text-sm font-medium truncate">
              {currentPipeline?.requirement || '加载中...'}
            </h2>
            <p className="text-xs text-muted-foreground">
              状态: {currentPipeline?.status} · 当前阶段: {currentPipeline?.currentStage}
            </p>
          </div>
          <MessageStream messages={messages} />
          {activeStage && <ActionBar stage={activeStage} />}
        </div>
        <div className="w-96 border-l bg-muted/20 flex flex-col">
          {activeStage?.stageType === 'coding' || activeStage?.stageType === 'testing' ? (
            <>
              <div className="flex-1 overflow-y-auto p-4">
                <h3 className="text-sm font-semibold mb-2">代码文件</h3>
                {codeFiles.length === 0 && (
                  <p className="text-xs text-muted-foreground">暂无代码文件</p>
                )}
                <div className="space-y-1">
                  {codeFiles.map((file) => (
                    <div
                      key={file.id}
                      className="text-sm p-2 rounded hover:bg-muted cursor-pointer truncate"
                    >
                      {file.path}
                    </div>
                  ))}
                </div>
              </div>
              <div className="h-1/2 border-t p-4">
                <h3 className="text-sm font-semibold mb-2">终端</h3>
                <div className="text-xs text-muted-foreground font-mono whitespace-pre-wrap">
                  {activeStage?.status === 'running' ? '测试中...' : '等待测试'}
                </div>
              </div>
            </>
          ) : stageArtifact ? (
            <div className="flex-1 overflow-y-auto p-4">
              <h3 className="text-sm font-semibold mb-2">{stageArtifact.title}</h3>
              <div className="prose prose-sm dark:prose-invert max-w-none">
                <pre className="whitespace-pre-wrap text-xs">{stageArtifact.content}</pre>
              </div>
            </div>
          ) : (
            <div className="flex-1 flex items-center justify-center text-muted-foreground text-sm">
              选择阶段查看详情
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
