/*******************************************************************************
 * Copyright (c) 2026 Red Hat, IBM Corporation and others.
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
package com.autotune.common.datasource;

import com.autotune.utils.KruizeConstants;

/**
 * Pure-function utility for substituting placeholders in workload query templates.
 * Kept logger-free so that it is trivially unit-testable.
 */
public final class WorkloadQueryUtil {

    private WorkloadQueryUtil() {}

    /**
     * Substitutes {@code LABEL_FILTER} and {@code ADDITIONAL_LABEL} placeholders in a
     * workload query template.
     * <p>
     * {@code LABEL_FILTER} is <em>always</em> replaced (with the built filter value or with
     * {@code ""} when absent) so no raw placeholder ever reaches Prometheus.
     * Stray commas produced by empty substitutions are cleaned up at the end.
     *
     * @param workloadQuery         the raw query template
     * @param includePodLabelFilter include-side PromQL label filter string (may be empty)
     * @param excludePodLabelFilter exclude-side PromQL label filter string (may be empty)
     * @param hasLabelFilter        whether a real label filter is present
     * @return the query with all placeholders resolved and stray commas removed
     */
    public static String substituteWorkloadQueryPlaceholders(String workloadQuery,
                                                             String includePodLabelFilter,
                                                             String excludePodLabelFilter,
                                                             boolean hasLabelFilter) {
        if (hasLabelFilter) {
            StringBuilder labelFilter = new StringBuilder();
            if (!includePodLabelFilter.isEmpty()) labelFilter.append(includePodLabelFilter);
            if (!excludePodLabelFilter.isEmpty()) {
                if (labelFilter.length() > 0) labelFilter.append(",");
                labelFilter.append(excludePodLabelFilter);
            }
            workloadQuery = workloadQuery.replace(KruizeConstants.KRUIZE_BULK_API.LABEL_FILTER, labelFilter.toString());
        } else {
            workloadQuery = workloadQuery.replace(KruizeConstants.KRUIZE_BULK_API.LABEL_FILTER, "");
        }

        workloadQuery = workloadQuery.replace(KruizeConstants.KRUIZE_BULK_API.ADDITIONAL_LABEL, "");

        // Clean up stray commas left after placeholder substitution (leading, trailing, or consecutive)
        workloadQuery = workloadQuery.replaceAll(",{2,}", ",").replaceAll(",\\s*}", "}").replaceAll("\\{,", "{");

        return workloadQuery;
    }
}
