'use client';

import { useQuery } from '@tanstack/react-query';
import { projectApi } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { Navbar } from '@/components/layout/navbar';
import { ProjectCard } from '@/components/project/project-card';
import { NewProjectDialog } from '@/components/project/new-project-dialog';
import { Skeleton } from '@/components/ui/skeleton';

export default function DashboardPage() {
  useAuth();
  const { data: projects, isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: projectApi.list,
  });

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="container mx-auto px-4 py-8 flex-1">
        <div className="flex items-center justify-between mb-8">
          <h1 className="text-2xl font-bold">项目仪表盘</h1>
          <NewProjectDialog />
        </div>

        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-32" />
            ))}
          </div>
        ) : projects?.length ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {projects.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        ) : (
          <div className="text-center py-20 text-muted-foreground">
            <p>还没有项目</p>
            <p className="text-sm">点击「新建项目」开始</p>
          </div>
        )}
      </main>
    </div>
  );
}
