/*
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
package com.btkelly.gnag.reporters

import com.btkelly.gnag.extensions.ReporterExtension
import com.btkelly.gnag.models.Violation
import com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask
import groovy.util.slurpersupport.GPathResult
import org.gradle.api.Project

import static com.btkelly.gnag.utils.StringUtils.sanitize
/**
 * Created by bobbake4 on 4/1/16.
 */
class CheckstyleViolationDetector extends BaseExecutedViolationDetector {

    CheckstyleViolationDetector(ReporterExtension reporterExtension, Project project) {
        super(reporterExtension, project)
    }

    @Override
    void executeReporter() {

        CheckstyleAntTask checkStyleTask = new CheckstyleAntTask()
        checkStyleTask.project = project.ant.antProject
        checkStyleTask.failOnViolation = false
        checkStyleTask.addFormatter(new CheckstyleAntTask.Formatter(type: new CheckstyleAntTask.FormatterType(value: 'xml'), tofile: reportFile()))

        if (reporterExtension.hasReporterConfig()) {
            checkStyleTask.setConfig(reporterExtension.getReporterConfig())
        } else {
            checkStyleTask.setConfigUrl(getClass().getClassLoader().getResource("checkstyle.xml"))
        }

        reportHelper.getAndroidSources().findAll { it.exists() }.each {
            checkStyleTask.addFileset(project.ant.fileset(dir: it))
        }

        checkStyleTask.perform()
    }

    @Override
    List<Violation> getDetectedViolations() {
        GPathResult xml = new XmlSlurper().parseText(reportFile().text)

        final List<Violation> result = new ArrayList<>()
        
        xml.file.each { file ->
                file.error.each { violation ->
                        final Integer lineNumber;
    
                        try {
                            lineNumber = violation.@line.toInteger()
                        } catch (final NumberFormatException e) {
                            System.out.println("Error reading line number from Checkstyle violations.");
                            e.printStackTrace();
                            lineNumber = null
                        }
                    
                        final String violationName = violation.@source.text()
                    
                        result.add(new Violation(
                                sanitize((String) violationName.substring(violationName.lastIndexOf(".") + 1)),
                                sanitize((String) name()),
                                sanitize((String) violation.@message.text()),
                                null,
                                sanitize((String) file.@name.text())
                                        .replace(project.rootDir.absolutePath + "/", ""),
                                lineNumber))
                }
        }
        
        return result
    }

    @Override
    String name() {
        return "Checkstyle"
    }

    @Override
    File reportFile() {
        return new File(reportHelper.getReportsDir(), "checkstyle_report.xml")
    }
    
}
