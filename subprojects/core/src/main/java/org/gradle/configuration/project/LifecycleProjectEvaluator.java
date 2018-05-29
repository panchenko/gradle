/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.configuration.project;

import org.gradle.api.Action;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Manages lifecycle concerns while delegating actual evaluation to another evaluator
 */
public class LifecycleProjectEvaluator implements ProjectEvaluator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleProjectEvaluator.class);

    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectEvaluator delegate;

    public LifecycleProjectEvaluator(BuildOperationExecutor buildOperationExecutor, ProjectEvaluator delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    public void evaluate(final ProjectInternal project, final ProjectStateInternal state) {
        if (state.getExecuted() || state.getExecuting()) {
            return;
        }
        buildOperationExecutor.run(new ConfigureProject(project, state));
    }

    private void doConfigure(ProjectInternal project, ProjectStateInternal state) {
        ExecuteBeforeEvaluateHooks beforeEvaluateHooks = new ExecuteBeforeEvaluateHooks(project, state);
        buildOperationExecutor.run(beforeEvaluateHooks);
        if (beforeEvaluateHooks.failed) {
            return;
        }

        state.setExecuting(true);
        try {
            delegate.evaluate(project, state);
        } catch (Exception e) {
            addConfigurationFailure(project, state, e);
        } finally {
            state.setExecuting(false);
            state.executed();
            buildOperationExecutor.run(new AfterBeforeEvaluateHooks(project, state));
        }
    }

    private void notifyAfterEvaluate(final ProjectInternal project, final ProjectStateInternal state) {
        @Nullable ProjectEvaluationListener nextBatch = project.getProjectEvaluationBroadcaster();
        do {
            try {
                nextBatch = project.stepEvaluationListener(nextBatch, new Action<ProjectEvaluationListener>() {
                    @Override
                    public void execute(ProjectEvaluationListener listener) {
                        listener.afterEvaluate(project, state);
                    }
                });
            } catch (Exception e) {
                onAfterEvaluateFailure(e, project, state);
                return;
            }
        } while (nextBatch != null);
    }

    private void onAfterEvaluateFailure(Exception e, ProjectInternal project, ProjectStateInternal state) {
        if (state.hasFailure()) {
            // Just log this failure, and pass the existing failure out in the project state
            boolean logStackTraces = project.getGradle().getStartParameter().getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;
            String infoMessage = "Project evaluation failed including an error in afterEvaluate {}.";
            if (logStackTraces) {
                LOGGER.error(infoMessage, e);
            } else {
                LOGGER.error(infoMessage + " Run with --stacktrace for details of the afterEvaluate {} error.");
            }
            return;
        }
        addConfigurationFailure(project, state, e);
    }

    private void addConfigurationFailure(ProjectInternal project, ProjectStateInternal state, Exception e) {
        ProjectConfigurationException failure = new ProjectConfigurationException(String.format("A problem occurred configuring %s.", project.getDisplayName()), e);
        state.executed(failure);
    }

    private class ConfigureProject implements RunnableBuildOperation {

        private ProjectInternal project;
        private ProjectStateInternal state;

        private ConfigureProject(ProjectInternal project, ProjectStateInternal state) {
            this.project = project;
            this.state = state;
        }

        @Override
        public void run(BuildOperationContext context) {
            doConfigure(project, state);
            state.rethrowFailure();
            context.setResult(ConfigureProjectBuildOperationType.RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            String name = "Configure project " + project.getIdentityPath();
            return BuildOperationDescriptor.displayName(name)
                .operationType(BuildOperationCategory.CONFIGURE_PROJECT)
                .details(new ConfigureProjectBuildOperationType.DetailsImpl(project.getProjectPath(), project.getGradle().getIdentityPath()));
        }

    }

    private class ExecuteBeforeEvaluateHooks implements RunnableBuildOperation {

        private final ProjectInternal project;
        private final ProjectStateInternal state;
        private boolean failed;

        private ExecuteBeforeEvaluateHooks(ProjectInternal project, ProjectStateInternal state) {
            this.project = project;
            this.state = state;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                project.getProjectEvaluationBroadcaster().beforeEvaluate(project);
                context.setResult(ProjectBeforeEvaluatedBuildOperationType.RESULT);
            } catch (Exception e) {
                addConfigurationFailure(project, state, e);
                failed = true;
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            String suffix =  " (" + project.getIdentityPath() + ")";
            return BuildOperationDescriptor.displayName("Execute beforeEvaluate hooks" + suffix)
                .progressDisplayName("Executing beforeEvaluate hooks" + suffix)
                .details(new ProjectBeforeEvaluatedBuildOperationType.DetailsImpl(project.getProjectPath(), project.getGradle().getIdentityPath()));
        }
    }

    private class AfterBeforeEvaluateHooks implements RunnableBuildOperation {

        private final ProjectInternal project;
        private final ProjectStateInternal state;

        private AfterBeforeEvaluateHooks(ProjectInternal project, ProjectStateInternal state) {
            this.project = project;
            this.state = state;
        }

        @Override
        public void run(BuildOperationContext context) {
            notifyAfterEvaluate(project, state);
            context.setResult(ProjectAfterEvaluatedBuildOperationType.RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            String suffix =  " (" + project.getIdentityPath() + ")";
            return BuildOperationDescriptor.displayName("Execute afterEvaluate hooks" + suffix)
                .progressDisplayName("Executing afterEvaluate hooks" + suffix)
                .details(new ProjectAfterEvaluatedBuildOperationType.DetailsImpl(project.getProjectPath(), project.getGradle().getIdentityPath()));
        }
    }
}
