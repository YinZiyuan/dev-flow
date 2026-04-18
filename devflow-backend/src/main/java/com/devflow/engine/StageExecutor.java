package com.devflow.engine;

import com.devflow.domain.pipeline.model.StageRun;
import com.devflow.domain.pipeline.model.StageType;

public interface StageExecutor {
    boolean supports(StageType type);
    void execute(StageRun stageRun);
}
