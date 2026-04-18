'use client';

import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { pipelineApi } from '@/lib/api';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';

interface NewPipelineDialogProps {
  projectId: string;
}

export function NewPipelineDialog({ projectId }: NewPipelineDialogProps) {
  const [open, setOpen] = useState(false);
  const [requirement, setRequirement] = useState('');
  const queryClient = useQueryClient();
  const router = useRouter();

  const createMutation = useMutation({
    mutationFn: (req: string) => pipelineApi.create(projectId, { requirement: req }),
  });

  useEffect(() => {
    if (createMutation.isSuccess && createMutation.data) {
      queryClient.invalidateQueries({ queryKey: ['pipelines', projectId] });
      setOpen(false);
      setRequirement('');
      router.push(`/runs/${createMutation.data.id}`);
    }
  }, [createMutation.isSuccess, createMutation.data, queryClient, projectId, router]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createMutation.mutate(requirement);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button>新建流水线</Button>} />
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>新建流水线</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label>需求描述</Label>
            <Textarea
              value={requirement}
              onChange={(e) => setRequirement(e.target.value)}
              placeholder="描述你想要实现的功能..."
              rows={5}
              required
            />
          </div>
          <Button type="submit" className="w-full" disabled={createMutation.isPending}>
            {createMutation.isPending ? '创建中...' : '开始'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
