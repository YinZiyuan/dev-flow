'use client';

import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { projectApi, pipelineApi } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { Navbar } from '@/components/layout/navbar';
import { NewPipelineDialog } from '@/components/project/new-pipeline-dialog';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import Link from 'next/link';

export default function ProjectDetailPage() {
  useAuth();
  const params = useParams();
  const projectId = params.id as string;

  const { data: project } = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => projectApi.get(projectId),
  });

  const { data: pipelines } = useQuery({
    queryKey: ['pipelines', projectId],
    queryFn: () => pipelineApi.list(projectId),
  });

  const statusMap: Record<string, string> = {
    running: '运行中',
    waiting_human: '等待人工',
    completed: '已完成',
    failed: '失败',
  };

  const statusColor: Record<string, string> = {
    running: 'bg-blue-500',
    waiting_human: 'bg-yellow-500',
    completed: 'bg-green-500',
    failed: 'bg-red-500',
  };

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="container mx-auto px-4 py-8 flex-1">
        {project && (
          <div className="mb-8">
            <div className="flex items-center justify-between mb-4">
              <h1 className="text-2xl font-bold">{project.name}</h1>
              <NewPipelineDialog projectId={projectId} />
            </div>
            <div className="text-sm text-muted-foreground space-y-1">
              {project.description && <p>{project.description}</p>}
              {project.techStack && <p>技术栈: {project.techStack}</p>}
              {project.repoPath && <p>路径: {project.repoPath}</p>}
            </div>
          </div>
        )}

        <h2 className="text-lg font-semibold mb-4">流水线历史</h2>
        {pipelines?.length ? (
          <div className="space-y-3">
            {pipelines.map((pipeline) => (
              <Link key={pipeline.id} href={`/runs/${pipeline.id}`}>
                <Card className="hover:border-primary cursor-pointer transition-colors">
                  <CardContent className="flex items-center justify-between py-4">
                    <div className="flex-1 min-w-0">
                      <p className="font-medium truncate">{pipeline.requirement}</p>
                      <p className="text-xs text-muted-foreground">
                        当前阶段: {pipeline.currentStage} ·{' '}
                        {new Date(pipeline.createdAt).toLocaleDateString('zh-CN')}
                      </p>
                    </div>
                    <Badge className={statusColor[pipeline.status]}>
                      {statusMap[pipeline.status] || pipeline.status}
                    </Badge>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        ) : (
          <div className="text-center py-12 text-muted-foreground">
            <p>还没有流水线</p>
            <p className="text-sm">点击「新建流水线」开始</p>
          </div>
        )}
      </main>
    </div>
  );
}
