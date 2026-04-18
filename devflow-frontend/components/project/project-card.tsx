'use client';

import Link from 'next/link';
import { Project } from '@/types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

interface ProjectCardProps {
  project: Project;
}

export function ProjectCard({ project }: ProjectCardProps) {
  return (
    <Link href={`/projects/${project.id}`}>
      <Card className="hover:border-primary cursor-pointer transition-colors">
        <CardHeader className="pb-2">
          <CardTitle className="text-lg">{project.name}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-1 text-sm text-muted-foreground">
          {project.techStack && <p>技术栈: {project.techStack}</p>}
          {project.repoPath && <p>路径: {project.repoPath}</p>}
          <p className="text-xs">
            创建于 {new Date(project.createdAt).toLocaleDateString('zh-CN')}
          </p>
        </CardContent>
      </Card>
    </Link>
  );
}
