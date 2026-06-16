/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
package com.autotune.common.bulk;

import com.autotune.analyzer.serviceObjects.BulkInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkServiceValidationTest {

    @Test
    @DisplayName("Validate time range should allow empty time range")
    void validateTimeRangeShouldAllowEmptyTimeRange() {
        assertEquals("", BulkServiceValidation.validateTimeRange(null));
    }

    @Test
    @DisplayName("Validate time range should reject invalid date format")
    void validateTimeRangeShouldRejectInvalidDateFormat() {
        BulkInput.TimeRange timeRange = new BulkInput.TimeRange();
        timeRange.setStart("2026/01/01 10:00:00");
        timeRange.setEnd("2026-01-01T11:00:00Z");

        assertEquals(
                com.autotune.utils.KruizeConstants.KRUIZE_BULK_API.INVALID_DATE_FORMAT,
                BulkServiceValidation.validateTimeRange(timeRange)
        );
    }

    @Test
    @DisplayName("Validate time range should reject start after end")
    void validateTimeRangeShouldRejectStartAfterEnd() {
        BulkInput.TimeRange timeRange = new BulkInput.TimeRange();
        timeRange.setStart("2026-01-01T12:00:00Z");
        timeRange.setEnd("2026-01-01T11:00:00Z");

        assertEquals(
                com.autotune.utils.KruizeConstants.KRUIZE_BULK_API.INVALID_START_TIME,
                BulkServiceValidation.validateTimeRange(timeRange)
        );
    }

    @Test
    @DisplayName("BulkInput should preserve datasources list for validation path")
    void bulkInputShouldPreserveDatasourcesListForValidationPath() {
        BulkInput bulkInput = new BulkInput();
        bulkInput.setDatasources(java.util.Arrays.asList("cryostat-1", "thanos-2"));

        assertEquals(2, bulkInput.getDatasources().size());
        assertEquals("cryostat-1", bulkInput.getDatasources().get(0));
        assertEquals("thanos-2", bulkInput.getDatasources().get(1));
    }

    @Test
    @DisplayName("BulkInput should preserve deprecated datasource when list is absent")
    void bulkInputShouldPreserveDeprecatedDatasourceWhenListIsAbsent() {
        BulkInput bulkInput = new BulkInput();
        bulkInput.setDatasource("prometheus-1");

        assertEquals("prometheus-1", bulkInput.getDatasource());
    }
}
