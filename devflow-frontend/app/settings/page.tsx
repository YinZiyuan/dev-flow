'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { agentConfigApi } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { Navbar } from '@/components/layout/navbar';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useState } from 'react';
import { AgentConfig } from '@/types';

const stageNames: Record<string, string> = {
  requirements: '需求分析',
  planning: '实施规划',
  coding: '代码生成',
  testing: '测试验证',
};

function AgentConfigCard({ config }: { config: AgentConfig }) {
  const [editing, setEditing] = useState(false);
  const [systemPrompt, setSystemPrompt] = useState(config.systemPrompt);
  const [model, setModel] = useState(config.model);
  const [maxTokens, setMaxTokens] = useState(config.maxTokens);
  const [maxRetries, setMaxRetries] = useState(config.maxRetries);
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: () =>
      agentConfigApi.update(config.id, {
        systemPrompt,
        model,
        maxTokens,
        maxRetries,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent-configs'] });
      setEditing(false);
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          {stageNames[config.stageType]} — {config.name}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {editing ? (
          <>
            <div className="space-y-2">
              <Label>System Prompt</Label>
              <Textarea
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                rows={6}
              />
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label>模型</Label>
                <Input value={model} onChange={(e) => setModel(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label>Max Tokens</Label>
                <Input
                  type="number"
                  value={maxTokens}
                  onChange={(e) => setMaxTokens(Number(e.target.value))}
                />
              </div>
              <div className="space-y-2">
                <Label>Max Retries</Label>
                <Input
                  type="number"
                  value={maxRetries}
                  onChange={(e) => setMaxRetries(Number(e.target.value))}
                />
              </div>
            </div>
            <div className="flex gap-2">
              <Button onClick={() => updateMutation.mutate()} disabled={updateMutation.isPending}>
                保存
              </Button>
              <Button variant="outline" onClick={() => setEditing(false)}>
                取消
              </Button>
            </div>
          </>
        ) : (
          <>
            <div className="text-sm text-muted-foreground line-clamp-3">
              {config.systemPrompt}
            </div>
            <div className="flex items-center justify-between text-sm">
              <span>
                模型: {config.model} · Tokens: {config.maxTokens} · Retries: {config.maxRetries}
              </span>
              <Button size="sm" variant="outline" onClick={() => setEditing(true)}>
                编辑
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

export default function SettingsPage() {
  useAuth();
  const { data: configs, isLoading } = useQuery({
    queryKey: ['agent-configs'],
    queryFn: agentConfigApi.list,
  });

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="container mx-auto px-4 py-8 flex-1">
        <h1 className="text-2xl font-bold mb-8">设置</h1>
        <h2 className="text-lg font-semibold mb-4">Agent 配置</h2>
        {isLoading ? (
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-40 bg-muted rounded-lg animate-pulse" />
            ))}
          </div>
        ) : (
          <div className="space-y-4">
            {configs?.map((config) => (
              <AgentConfigCard key={config.id} config={config} />
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
