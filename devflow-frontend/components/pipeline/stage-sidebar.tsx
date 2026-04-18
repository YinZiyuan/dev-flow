'use client';

import { StageRun, StageType, StageStatus } from '@/types';
import { CheckCircle2, Circle, Loader2, AlertCircle, SkipForward, Clock } from 'lucide-react';
import { cn } from '@/lib/utils';

interface StageSidebarProps {
  stages: StageRun[];
  currentStageId: string | null;
  onSelectStage: (stage: StageRun) => void;
}

const stageNames: Record<StageType, string> = {
  requirements: '需求分析',
  planning: '实施规划',
  coding: '代码生成',
  testing: '测试验证',
};

function StageIcon({ status }: { status: StageStatus }) {
  switch (status) {
    case 'completed':
      return <CheckCircle2 className="w-5 h-5 text-green-500" />;
    case 'running':
      return <Loader2 className="w-5 h-5 text-blue-500 animate-spin" />;
    case 'failed':
      return <AlertCircle className="w-5 h-5 text-red-500" />;
    case 'skipped':
      return <SkipForward className="w-5 h-5 text-gray-400" />;
    case 'waiting_answer':
    case 'waiting_choice':
    case 'waiting_approval':
    case 'waiting_revision':
      return <Clock className="w-5 h-5 text-yellow-500" />;
    default:
      return <Circle className="w-5 h-5 text-gray-300" />;
  }
}

export function StageSidebar({ stages, currentStageId, onSelectStage }: StageSidebarProps) {
  return (
    <div className="w-64 border-r bg-muted/30 p-4 flex flex-col gap-2"
    >
      <h3 className="text-sm font-semibold text-muted-foreground mb-2"
      >流程进度</h3>
      {stages.map((stage) => (
        <button
          key={stage.id}
          onClick={() => onSelectStage(stage)}
          className={cn(
            'flex items-center gap-3 p-3 rounded-lg text-left transition-colors w-full',
            currentStageId === stage.id
              ? 'bg-primary/10 border border-primary/20'
              : 'hover:bg-muted border border-transparent'
          )}
        >
          <StageIcon status={stage.status} />
          <div className="flex-1 min-w-0"
          >
            <p className="text-sm font-medium truncate"
            >{stageNames[stage.stageType]}</p>
            <p className="text-xs text-muted-foreground"
            >{stage.status}</p>
          </div>
          {stage.orderIndex > 0 && (
            <span className="text-xs text-muted-foreground"
            >#{stage.orderIndex + 1}</span>
          )}
        </button>
      ))}
    </div>
  );
}
