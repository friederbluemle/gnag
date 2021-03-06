/**
 * Copyright 2016 Bryan Kelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.btkelly.gnag.tasks;

import com.btkelly.gnag.extensions.GnagPluginExtension;
import com.btkelly.gnag.models.CheckStatus;
import com.btkelly.gnag.models.Violation;
import com.btkelly.gnag.reporters.*;
import com.btkelly.gnag.utils.ReportHelper;
import com.btkelly.gnag.utils.ReportWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.*;

import static com.btkelly.gnag.models.GitHubStatusType.FAILURE;
import static com.btkelly.gnag.utils.ReportWriter.REPORT_FILE_NAME;

/**
 * Created by bobbake4 on 4/1/16.
 */
public class GnagCheck extends DefaultTask {

    public static final String TASK_NAME = "gnagCheck";

    public static void addTask(Project project, GnagPluginExtension gnagPluginExtension) {
        Map<String, Object> taskOptions = new HashMap<>();

        taskOptions.put(Task.TASK_NAME, TASK_NAME);
        taskOptions.put(Task.TASK_TYPE, GnagCheck.class);
        taskOptions.put(Task.TASK_GROUP, "Verification");
        taskOptions.put(Task.TASK_DEPENDS_ON, "check");
        taskOptions.put(Task.TASK_DESCRIPTION, "Runs Gnag checks and generates an HTML report");

        GnagCheck gnagCheckTask = (GnagCheck) project.task(taskOptions, TASK_NAME);
        gnagCheckTask.setGnagPluginExtension(gnagPluginExtension);
        gnagCheckTask.violationDetectors.add(new CheckstyleViolationDetector(gnagPluginExtension.checkstyle, project));
        gnagCheckTask.violationDetectors.add(new PMDViolationDetector(gnagPluginExtension.pmd, project));
        gnagCheckTask.violationDetectors.add(new FindbugsViolationDetector(gnagPluginExtension.findbugs, project));
        gnagCheckTask.violationDetectors.add(new AndroidLintViolationDetector(gnagPluginExtension.androidLint, project));
    }

    private final List<ViolationDetector> violationDetectors = new ArrayList<>();
    private final ReportHelper reportHelper = new ReportHelper(getProject());

    private GnagPluginExtension gnagPluginExtension;

    @SuppressWarnings("unused")
    @TaskAction
    public void taskAction() {
        if (gnagPluginExtension.isEnabled()) {
            executeGnagCheck();
        }
    }

    private void executeGnagCheck() {
        final Set<Violation> allDetectedViolations = new HashSet<>();
        
        violationDetectors
                .stream()
                .filter(ViolationDetector::isEnabled)
                .forEach(violationDetector -> {
                    
                        if (violationDetector instanceof BaseExecutedViolationDetector) {
                            ((BaseExecutedViolationDetector) violationDetector).executeReporter();
                        }

                        final List<Violation> detectedViolations = violationDetector.getDetectedViolations();
                        allDetectedViolations.addAll(detectedViolations);

                        System.out.println(
                                violationDetector.name() + " detected " + detectedViolations.size() + " violations.");
                });

        ReportWriter.writeReportToDirectory(allDetectedViolations, reportHelper.getReportsDir());

        if (allDetectedViolations.isEmpty()) {
            getProject().setStatus(CheckStatus.getSuccessfulCheckStatus());
        } else {
            getProject().setStatus(new CheckStatus(FAILURE, allDetectedViolations));

            final TaskExecutionGraph taskGraph = getProject().getGradle().getTaskGraph();

            boolean hasReportTask = false;

            for (final Task task : taskGraph.getAllTasks()) {
                if (task.getName().equals(GnagReportTask.TASK_NAME)) {
                    hasReportTask = true;
                }
            }

            final String failedMessage
                    = "One or more violation detectors has found violations. Check the report at "
                      + reportHelper.getReportsDir()
                      + File.separatorChar
                      + REPORT_FILE_NAME + " for details.";

            if (gnagPluginExtension.shouldFailOnError() && !hasReportTask) {
                throw new GradleException(failedMessage);
            } else {
                System.out.println(failedMessage);
                throw new StopExecutionException(failedMessage);
            } 
        }
    }

    private void setGnagPluginExtension(GnagPluginExtension gnagPluginExtension) {
        this.gnagPluginExtension = gnagPluginExtension;
    }
    
}
