'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { stageApi } from '@/lib/api';
import { StageRun, StageStatus, ChoiceOption } from '@/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useAppStore } from '@/store/app-store';

interface ActionBarProps {
  stage: StageRun;
}

export function ActionBar({ stage }: ActionBarProps) {
  const [answer, setAnswer] = useState('');
  const [feedback, setFeedback] = useState('');
  const { messages, setMessages, clearStreamText } = useAppStore();
  const queryClient = useQueryClient();

  const answerMutation = useMutation({
    mutationFn: (content: string) => stageApi.answer(stage.id, { content }),
    onSuccess: () => {
      setAnswer('');
      clearStreamText();
      queryClient.invalidateQueries({ queryKey: ['messages', stage.id] });
      queryClient.invalidateQueries({ queryKey: ['stages'] });
    },
  });

  const chooseMutation = useMutation({
    mutationFn: (optionId: string) => stageApi.choose(stage.id, { optionId }),
    onSuccess: () => {
      clearStreamText();
      queryClient.invalidateQueries({ queryKey: ['messages', stage.id] });
      queryClient.invalidateQueries({ queryKey: ['stages'] });
    },
  });

  const approveMutation = useMutation({
    mutationFn: () => stageApi.approve(stage.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stages'] });
      queryClient.invalidateQueries({ queryKey: ['pipeline'] });
    },
  });

  const reviseMutation = useMutation({
    mutationFn: (fb: string) => stageApi.revise(stage.id, { feedback: fb }),
    onSuccess: () => {
      setFeedback('');
      clearStreamText();
      queryClient.invalidateQueries({ queryKey: ['messages', stage.id] });
      queryClient.invalidateQueries({ queryKey: ['stages'] });
    },
  });

  const lastMessage = messages[messages.length - 1];
  let choiceOptions: ChoiceOption[] = [];
  if (lastMessage?.type === 'choice_request' && lastMessage.options) {
    try {
      const parsed = JSON.parse(lastMessage.content);
      choiceOptions = parsed.options || [];
    } catch {}
  }

  switch (stage.status) {
    case 'waiting_answer':
      return (
        <div className="border-t p-4 bg-background"
        >
          <div className="flex gap-2"
          >
            <Input
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              placeholder="输入你的回答..."
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  answerMutation.mutate(answer);
                }
              }}
            />
            <Button
              onClick={() => answerMutation.mutate(answer)}
              disabled={answerMutation.isPending || !answer.trim()}
            >
              发送
            </Button>
          </div>
        </div>
      );

    case 'waiting_choice':
      return (
        <div className="border-t p-4 bg-background"
        >
          <p className="text-sm text-muted-foreground mb-2"
          >请选择一个方案：</p>
          <div className="flex gap-2 flex-wrap"
          >
            {choiceOptions.map((opt) => (
              <Button
                key={opt.id}
                variant="outline"
                onClick={() => chooseMutation.mutate(opt.id)}
                disabled={chooseMutation.isPending}
              >
                {opt.label}
              </Button>
            ))}
          </div>
        </div>
      );

    case 'waiting_approval':
      return (
        <div className="border-t p-4 bg-background space-y-3"
        >
          <div className="flex gap-2"
          >
            <Button
              onClick={() => approveMutation.mutate()}
              disabled={approveMutation.isPending}
              className="flex-1"
            >
              批准进入下一阶段
            </Button>
          </div>
          <div className="flex gap-2"
          >
            <Textarea
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
              placeholder="输入修改意见（可选）..."
              rows={2}
              className="flex-1"
            />
            <Button
              variant="secondary"
              onClick={() => reviseMutation.mutate(feedback)}
              disabled={reviseMutation.isPending}
            >
              要求修改
            </Button>
          </div>
        </div>
      );

    case 'running':
      return (
        <div className="border-t p-4 bg-background text-center text-sm text-muted-foreground"
        >
          <span className="inline-block w-2 h-2 bg-blue-500 rounded-full animate-pulse mr-2"
          />
          Agent 正在工作中...
        </div>
      );

    case 'failed':
      return (
        <div className="border-t p-4 bg-background"
        >
          <p className="text-sm text-red-500 mb-2"
          >阶段执行失败</p>
          <Button
            variant="outline"
            onClick={() => answerMutation.mutate('请重试')}
          >
            重试
          </Button>
        </div>
      );

    default:
      return (
        <div className="border-t p-4 bg-background text-center text-sm text-muted-foreground"
        >
          等待中...
        </div>
      );
  }
}
