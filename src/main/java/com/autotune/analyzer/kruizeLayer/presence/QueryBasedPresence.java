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

package com.autotune.analyzer.kruizeLayer.presence;

import com.autotune.analyzer.kruizeLayer.LayerPresenceQuery;
import com.autotune.analyzer.kruizeLayer.utils.LayerUtils;
import com.autotune.analyzer.utils.AnalyzerConstants.LayerConstants;
import com.autotune.analyzer.utils.AnalyzerConstants.LayerConstants.LogMessages;
import com.autotune.analyzer.utils.AnalyzerConstants.LayerConstants.PresenceType;
import com.autotune.common.datasource.DataSourceCollection;
import com.autotune.common.datasource.DataSourceInfo;
import com.autotune.common.datasource.DataSourceOperatorImpl;
import com.autotune.utils.KruizeConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.core.util.SystemNanoClock;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation for query-based layer presence detection
 */
public class QueryBasedPresence implements LayerPresenceDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryBasedPresence.class);

    private List<LayerPresenceQuery> queries;

    public QueryBasedPresence() {
        this.queries = new ArrayList<>();
    }

    public QueryBasedPresence(List<LayerPresenceQuery> queries) {
        this.queries = queries != null ? queries : new ArrayList<>();
    }

    @Override
    public PresenceType getType() {
        return PresenceType.QUERY;
    }

    /**
     * Detect layer presence using namespace and container name filtering
     * @param namespace The Kubernetes namespace
     * @param containerName The container name
     * @return true if layer is detected
     * @throws Exception if detection fails
     */
    @Override
    public boolean detectPresence(String namespace, String containerName, List<String> datasourceNames) throws Exception {
        if (queries == null || queries.isEmpty()) {
            LOGGER.warn(LogMessages.NO_QUERIES_DEFINED);
            return false;
        }

        if (datasourceNames == null || datasourceNames.isEmpty()) {
            LOGGER.warn("No datasource names provided for layer detection");
            return false;
        }

        List<DataSourceInfo> experimentDataSources = resolveExperimentDatasources(datasourceNames);
        // Iterate through all configured queries
        for (LayerPresenceQuery query : queries) {
            // Skip null query objects
            if (query == null) {
                LOGGER.warn(LogMessages.NULL_QUERY_ENCOUNTERED);
                continue;
            }

            for (DataSourceInfo dataSourceInfo : experimentDataSources) {
                try {
                    if (dataSourceInfo == null) {
                        LOGGER.warn("Encountered null datasource info while evaluating layer presence");
                        continue;
                    }

                    // Skip queries that don't match the datasource provider
                    if (query.getDataSource() != null &&
                            !isProviderCompatible(query.getDataSource(), dataSourceInfo.getProvider())) {
                        LOGGER.debug("Skipping query for datasource '{}' as it doesn't match current datasource provider '{}'",
                                query.getDataSource(), dataSourceInfo.getProvider());
                        continue;
                    }

                    // Get the appropriate operator for the datasource provider
                    DataSourceOperatorImpl operator = DataSourceOperatorImpl.getInstance()
                            .getOperator(dataSourceInfo.getProvider());

                    if (operator == null) {
                        LOGGER.warn(LogMessages.NO_OPERATOR_AVAILABLE, dataSourceInfo.getProvider());
                        continue;
                    }

                    // Start with the original query
                    String modifiedQuery = query.getLayerPresenceQuery();

                    if (query.getDataSource() != null &&
                            query.getDataSource().equalsIgnoreCase(KruizeConstants.SupportedDatasources.CRYOSTAT)) {
                        DataSourceInfo promDatasourceInfo = findFirstPromQlCapableDatasource(experimentDataSources);

                        if (promDatasourceInfo == null) {
                            LOGGER.warn("Cryostat layer detection requires a PromQL-capable datasource instance in the experiment datasource list, but none was found. Skipping Cryostat detection.");
                            continue;
                        }

                        DataSourceOperatorImpl prometheusOperator = DataSourceOperatorImpl.getInstance()
                                .getOperator(promDatasourceInfo.getProvider());

                        // Build PromQL query to get pods
                        String promQl = KruizeConstants.PromQueries.GET_PODS_WITH_NS_CONTAINER;
                        if (namespace != null && !namespace.isBlank()) {
                            promQl = appendFilter(promQl, LayerConstants.LABEL_NAMESPACE, namespace);
                        }
                        if (containerName != null && !containerName.isBlank()) {
                            promQl = appendFilter(promQl, LayerConstants.LABEL_CONTAINER, containerName);
                        }
                        LOGGER.debug("PromQl: {}", promQl);

                        // Execute Prometheus query using the Prometheus datasource
                        JSONObject returnObj = prometheusOperator.getJsonObjectForQuery(promDatasourceInfo, promQl);
                        List<String> pods = LayerUtils.extractPods(returnObj);

                        // Get Cryostat operator and datasource
                        DataSourceOperatorImpl cryostatOperator = DataSourceOperatorImpl.getInstance()
                                .getOperator(KruizeConstants.SupportedDatasources.CRYOSTAT);
                        
                        if (cryostatOperator == null) {
                            LOGGER.error("Failed to get Cryostat operator instance. Cannot proceed with Cryostat layer detection.");
                            continue;
                        }
                        
                        LOGGER.info("Found {} pod(s) to check for Cryostat targets: {}", pods.size(), pods);
                        
                        if (!pods.isEmpty()) {
                            for (String pod: pods) {
                                LOGGER.debug("Checking Cryostat targets for pod: {}", pod);
                                String queryToTry = modifiedQuery.replace("$POD_NAME$", pod);
                                LOGGER.info("Executing Cryostat GraphQL query for pod '{}'. Query: {}", pod, queryToTry);

                                try {
                                    // Use the Cryostat datasource (dataSourceInfo) for GraphQL query
                                    JSONObject graphQlJson = cryostatOperator.getJsonObjectForQuery(dataSourceInfo, queryToTry);
                                    if (null == graphQlJson) {
                                        LOGGER.warn(
                                                "Cryostat query returned null response while checking layer presence. datasource='{}', provider='{}', namespace='{}', container='{}', pod='{}'. This could indicate: 1) Network connectivity issue, 2) Authentication failure, 3) Cryostat service unavailable. Skipping this pod.",
                                                dataSourceInfo.getName(),
                                                dataSourceInfo.getProvider(),
                                                namespace,
                                                containerName,
                                                pod
                                        );
                                        LOGGER.debug("Cryostat datasource URL: {}", dataSourceInfo.getUrl());
                                        continue;
                                    }

                                    LOGGER.debug("Received GraphQL response for pod '{}': {}", pod, graphQlJson);
                                    
                                    // Check if response has the expected structure
                                    if (!graphQlJson.has("data")) {
                                        LOGGER.warn("GraphQL response missing 'data' field for pod '{}'. Response: {}", pod, graphQlJson);
                                        continue;
                                    }
                                    
                                    JSONObject dataObj = graphQlJson.optJSONObject("data");
                                    if (dataObj == null) {
                                        LOGGER.warn("GraphQL 'data' field is not a JSON object for pod '{}'. Response: {}", pod, graphQlJson);
                                        continue;
                                    }
                                    
                                    JSONArray envNodes = dataObj.optJSONArray("environmentNodes");

                                    if (envNodes != null && !envNodes.isEmpty()) {
                                        LOGGER.info("SUCCESS: Found {} Cryostat target(s) for pod '{}' in namespace '{}', container '{}'",
                                            envNodes.length(), pod, namespace, containerName);
                                        LOGGER.debug("Environment nodes: {}", envNodes);
                                        return true;
                                    } else {
                                        LOGGER.debug("No Cryostat targets found for pod '{}'. environmentNodes is {}",
                                            pod, envNodes == null ? "null" : "empty");
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Exception while querying Cryostat for pod '{}': {}", pod, e.getMessage(), e);
                                    // Continue to next pod instead of failing completely
                                }
                            }
                            
                            LOGGER.info("Completed checking all {} pod(s) for Cryostat targets. No targets found.", pods.size());
                        } else {
                            LOGGER.warn("No pods found from Prometheus query for namespace='{}', container='{}'. Cannot check Cryostat targets.",
                                namespace, containerName);
                        }

                    } else {
                        // Append dynamic filters for namespace, container
                        if (namespace != null && !namespace.isBlank()) {
                            modifiedQuery = appendFilter(modifiedQuery, LayerConstants.LABEL_NAMESPACE, namespace);
                        }
                        if (containerName != null && !containerName.isBlank()) {
                            modifiedQuery = appendFilter(modifiedQuery, LayerConstants.LABEL_CONTAINER, containerName);
                        }
                        LOGGER.debug(LogMessages.EXECUTING_QUERY, modifiedQuery);

                        // Execute the modified query and get results
                        JsonArray resultArray = operator.getResultArrayForQuery(
                                dataSourceInfo,
                                modifiedQuery
                        );

                        // Check if we got any results - if yes, layer is present
                        if (resultArray != null && !resultArray.isEmpty()) {
                            LOGGER.debug(LogMessages.LAYER_DETECTED_VIA_QUERY, namespace, containerName);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error(LogMessages.ERROR_EXECUTING_QUERY, query.getDataSource(), e);
                }
            }
        }

        // No queries returned positive results
        return false;
    }

    /**
     * Appends a label filter to a PromQL query
     *
     * @param query The original PromQL query
     * @param labelName The label name to filter by
     * @param labelValue The label value
     * @return Modified query with the filter appended
     */
    private String appendFilter(String query, String labelName, String labelValue) {
        if (query == null || query.isBlank()) {
            return query;
        }
        if (labelName == null || labelName.isBlank()) {
            return query;
        }
        if (labelValue == null || labelValue.isBlank()) {
            return query;
        }

        // Escape the label value to prevent PromQL injection and syntax errors
        String filter = labelName + "=\"" + labelValue.replace("\\", "\\\\").replace("\"","\\\"").replace("\n", "\\n").replace("\t", "\\t").replace("\r","\\r") + "\"";

        // Find the first opening and closing brace
        int openBrace = query.indexOf('{');
        int closeBrace = query.indexOf('}');

        if (openBrace != -1 && closeBrace != -1 && closeBrace > openBrace) {
            // Braces exist - check if they contain content
            String existingFilters = query.substring(openBrace + 1, closeBrace).trim();

            if (existingFilters.isEmpty()) {
                // Empty braces - insert filter without comma
                return query.substring(0, openBrace + 1) + filter + query.substring(closeBrace);
            } else {
                // Braces with content - append with comma
                return query.substring(0, closeBrace) + "," + filter + query.substring(closeBrace);
            }
        } else {
            // No braces found - find the first space or end of metric name
            int spaceIndex = query.indexOf(' ');
            int insertPoint = (spaceIndex != -1) ? spaceIndex : query.length();

            // Insert braces with filter after the metric name
            return query.substring(0, insertPoint) + "{" + filter + "}" + query.substring(insertPoint);
        }
    }

    private List<DataSourceInfo> resolveExperimentDatasources(List<String> datasourceNames) {
        List<DataSourceInfo> resolvedDatasources = new ArrayList<>();
        if (datasourceNames == null || datasourceNames.isEmpty()) {
            return resolvedDatasources;
        }

        for (String datasourceName : datasourceNames) {
            DataSourceInfo dataSourceInfo = DataSourceCollection.getInstance()
                    .getDataSourcesCollection()
                    .get(datasourceName);
            if (dataSourceInfo == null) {
                LOGGER.warn(LogMessages.DATASOURCE_NOT_FOUND, datasourceName);
                continue;
            }
            resolvedDatasources.add(dataSourceInfo);
        }
        return resolvedDatasources;
    }

    private DataSourceInfo findFirstPromQlCapableDatasource(List<DataSourceInfo> experimentDataSources) {
        for (DataSourceInfo dataSourceInfo : experimentDataSources) {
            if (dataSourceInfo != null && isPromQlCapableProvider(dataSourceInfo.getProvider())) {
                return dataSourceInfo;
            }
        }
        return null;
    }

    private boolean isProviderCompatible(String queryDatasource, String datasourceProvider) {
        if (queryDatasource == null || datasourceProvider == null) {
            return false;
        }
        if (queryDatasource.equalsIgnoreCase(datasourceProvider)) {
            return true;
        }
        return isPromQlCapableProvider(queryDatasource) && isPromQlCapableProvider(datasourceProvider);
    }

    private boolean isPromQlCapableProvider(String provider) {
        return provider != null && (
                provider.equalsIgnoreCase(KruizeConstants.SupportedDatasources.PROMETHEUS) ||
                provider.equalsIgnoreCase(KruizeConstants.SupportedDatasources.THANOS)
        );
    }

    public List<LayerPresenceQuery> getQueries() {
        return queries;
    }

    public void setQueries(List<LayerPresenceQuery> queries) {
        this.queries = queries != null ? queries : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "QueryBasedPresence{" +
                "queries=" + queries +
                '}';
    }
}
