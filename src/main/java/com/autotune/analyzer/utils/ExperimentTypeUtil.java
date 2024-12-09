/*******************************************************************************
 * Copyright (c) 2024 Red Hat, IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.autotune.analyzer.utils;

/**
 * This class contains utility functions to determine experiment type
 */
public class ExperimentTypeUtil {
    public static boolean isContainerExperiment(AnalyzerConstants.ExperimentType experimentType) {
        return experimentType == null || AnalyzerConstants.ExperimentType.CONTAINER.equals(experimentType);
    }

    public static boolean isNamespaceExperiment(AnalyzerConstants.ExperimentType experimentType) {
        return experimentType != null && AnalyzerConstants.ExperimentType.NAMESPACE.equals(experimentType);
    }
}
