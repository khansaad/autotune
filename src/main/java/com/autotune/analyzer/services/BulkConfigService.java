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
package com.autotune.analyzer.services;

import com.autotune.analyzer.serviceObjects.BulkConfig;
import com.autotune.common.data.ValidationOutputData;
import com.autotune.database.dao.ExperimentDAO;
import com.autotune.database.dao.ExperimentDAOImpl;
import com.autotune.database.helper.DBConstants;
import com.autotune.database.table.lm.KruizeBulkConfigEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.CHARACTER_ENCODING;
import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.JSON_CONTENT_TYPE;

/**
 * Servlet handling CRUD operations for optimizer bulk configurations.
 *
 * <ul>
 *   <li>GET  /bulkConfigs                        — list all configs as a JSON array</li>
 *   <li>GET  /bulkConfigs?config_name=&lt;name&gt; — return a single config as a JSON array</li>
 *   <li>POST /bulkConfigs                        — create a new bulk config</li>
 * </ul>
 *
 * These endpoints are consumed by the kruize-optimizer service via its
 * {@code KruizeClient.getBulkConfigs()} REST client call.
 */
@WebServlet(asyncSupported = true)
public class BulkConfigService extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(BulkConfigService.class);

    /** Query-parameter name used by the optimizer client: {@code ?config_name=}. */
    private static final String PARAM_CONFIG_NAME = "config_name";

    private final ObjectMapper objectMapper;

    public BulkConfigService() {
        objectMapper = new ObjectMapper();
    }

    /**
     * GET /bulkConfigs
     * <p>
     * Without {@code config_name}: returns all configs as a JSON array.<br>
     * With    {@code config_name}: returns the matching config wrapped in a JSON array,
     * or HTTP 404 when not found.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(CHARACTER_ENCODING);

        String configName = request.getParameter(PARAM_CONFIG_NAME);
        ExperimentDAO dao = new ExperimentDAOImpl();

        try {
            if (configName != null && !configName.trim().isEmpty()) {
                // --- single config lookup ---
                KruizeBulkConfigEntry entry = dao.loadBulkConfigByName(configName.trim());
                if (entry == null) {
                    sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND,
                            String.format(DBConstants.BULK_CONFIG_MESSAGES.BULK_CONFIG_NOT_FOUND, configName));
                    return;
                }
                List<BulkConfig> result = List.of(entry.toBulkConfig());
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(objectMapper.writeValueAsString(result));
            } else {
                // --- list all configs ---
                List<KruizeBulkConfigEntry> entries = dao.loadAllBulkConfigs();
                List<BulkConfig> configs = entries.stream()
                        .map(KruizeBulkConfigEntry::toBulkConfig)
                        .collect(Collectors.toList());
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(objectMapper.writeValueAsString(configs));
            }
        } catch (Exception e) {
            LOGGER.error("Error fetching bulk configs: {}", e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * POST /bulkConfigs
     * <p>
     * Creates a new bulk config from the JSON request body.
     * Returns HTTP 201 on success, 409 if a config with the same name already exists.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(CHARACTER_ENCODING);

        BulkConfig config;
        try {
            config = objectMapper.readValue(request.getInputStream(), BulkConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse bulk config request body: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid request body: " + e.getMessage());
            return;
        }

        if (config.getConfigName() == null || config.getConfigName().trim().isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "config_name is required and cannot be empty");
            return;
        }

        Instant now = Instant.now();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        if (config.getEnabled() == null) {
            config.setEnabled(true);
        }

        KruizeBulkConfigEntry entry;
        try {
            entry = KruizeBulkConfigEntry.fromBulkConfig(config);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Validation failed for bulk config '{}': {}", config.getConfigName(), e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        ExperimentDAO dao = new ExperimentDAOImpl();
        ValidationOutputData result = dao.addBulkConfigToDB(entry);

        if (result.isSuccess()) {
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(
                    objectMapper.writeValueAsString(entry.toBulkConfig()));
        } else {
            int code = result.getErrorCode() != 0
                    ? result.getErrorCode()
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            sendErrorResponse(response, code, result.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendErrorResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        response.sendError(status, message);
    }
}
