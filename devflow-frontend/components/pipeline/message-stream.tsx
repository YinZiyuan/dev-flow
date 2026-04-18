'use client';

import { useRef, useEffect } from 'react';
import { Message } from '@/types';
import { useAppStore } from '@/store/app-store';
import { User, Bot } from 'lucide-react';
import { cn } from '@/lib/utils';
import ReactMarkdown from 'react-markdown';

interface MessageStreamProps {
  messages: Message[];
}

export function MessageStream({ messages }: MessageStreamProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const { streamText } = useAppStore();

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, streamText]);

  return (
    <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-4"
    >
      {messages.map((msg) => (
        <div
          key={msg.id}
          className={cn(
            'flex gap-3',
            msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'
          )}
        >
          <div className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center bg-muted"
          >
            {msg.role === 'user' ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
          </div>
          <div
            className={cn(
              'max-w-[80%] rounded-lg px-4 py-2 text-sm',
              msg.role === 'user'
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted'
            )}
          >
            {msg.type === 'choice_request' ? (
              <ChoiceMessage content={msg.content} />
            ) : (
              <div className="prose prose-sm dark:prose-invert max-w-none"
              >
                <ReactMarkdown>{msg.content}</ReactMarkdown>
              </div>
            )}
          </div>
        </div>
      ))}
      {streamText && (
        <div className="flex gap-3"
        >
          <div className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center bg-muted"
          >
            <Bot className="w-4 h-4" />
          </div>
          <div className="bg-muted rounded-lg px-4 py-2 text-sm max-w-[80%]"
          >
            <div className="prose prose-sm dark:prose-invert max-w-none"
            >
              <ReactMarkdown>{streamText}</ReactMarkdown>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ChoiceMessage({ content }: { content: string }) {
  try {
    const json = JSON.parse(content);
    if (json.type === 'choice' && json.options) {
      return (
        <div className="space-y-2"
        >
          <p>请选择一个方案：</p>
          {json.options.map((opt: any) => (
            <div key={opt.id} className="border rounded p-2 bg-background"
            >
              <p className="font-medium"
              >{opt.label}</p>
              <p className="text-xs text-muted-foreground"
              >{opt.description}</p>
            </div>
          ))}
        </div>
      );
    }
  } catch {}
  return <div className="prose prose-sm dark:prose-invert max-w-none"
  ><ReactMarkdown>{content}</ReactMarkdown></div>;
}
