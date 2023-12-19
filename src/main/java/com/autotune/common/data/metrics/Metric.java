/*******************************************************************************
 * Copyright (c) 2021, 2022 Red Hat, IBM Corporation and others.
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
package com.autotune.common.data.metrics;

import com.autotune.utils.KruizeConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the variables used in the objective_function for the autotune object
 * objective_function:
 *   function_type: expression
 *   expression: "transaction_response_time"
 * function_variables:
 * - name: "transaction_response_time"
 * query: "application_org_acme_microprofile_metrics_PrimeNumberChecker_checksTimer_mean_seconds"
 * datasource: "prometheus"
 * value_type: "double"
 */
public final class Metric {
    private String name;
    private String query;
    private String datasource;
    @SerializedName("value_type")
    private String valueType;
    @SerializedName("kubernetes_object")
    private String kubernetesObject;
    private final LinkedHashMap<String, MetricResults> trialSummaryResult = new LinkedHashMap<>();
    @SerializedName("results")
    private MetricResults metricResults;
    private final LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Integer, MetricResults>>> cycleDataMap = new LinkedHashMap<>();
    @SerializedName("aggregation_functions")
    @JsonAdapter(AggregationFunctionsDeserializer.class)
    private HashMap<String, AggregationFunctions> aggregationFunctionsMap;

    public Metric(String name,
                  String query,
                  String datasource,
                  String valueType,
                  String kubernetesObject) {
        this.name = name;
        this.query = query;
        this.datasource = datasource;
        this.valueType = valueType;
        this.kubernetesObject = kubernetesObject;
    }
    public Metric() {

    }
    public String getName() {
        return name;
    }

    public String getQuery() {
        return query;
    }

    public String getDatasource() {
        return datasource;
    }

    public String getValueType() {
        return valueType;
    }

    @JsonProperty(KruizeConstants.JSONKeys.RESULTS)
    public MetricResults getMetricResult() {
        return metricResults;
    }

    public void setMetricResult(MetricResults metricResults) {
        this.metricResults = metricResults;
    }

    public LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<Integer, MetricResults>>> getCycleDataMap() {
        return cycleDataMap;
    }

    public LinkedHashMap<String, MetricResults> getTrialSummaryResult() {
        return trialSummaryResult;
    }

    public String getKubernetesObject() {
        return kubernetesObject;
    }

    public void setKubernetesObject(String kubernetesObject) {
        this.kubernetesObject = kubernetesObject;
    }

    public HashMap<String, AggregationFunctions> getAggregationFunctionsMap() {
        return aggregationFunctionsMap;
    }

    public void setAggregationFunctionsMap(HashMap<String, AggregationFunctions> aggregationFunctionsMap) {
        this.aggregationFunctionsMap = aggregationFunctionsMap;
    }

    @Override
    public String toString() {
        return "Metric{" +
                "name='" + name + '\'' +
                ", query='" + query + '\'' +
                ", datasource='" + datasource + '\'' +
                ", valueType='" + valueType + '\'' +
                ", kubernetesObject='" + kubernetesObject + '\'' +
                ", aggregationFunctionsMap=" + aggregationFunctionsMap +
                '}';
    }
}
class AggregationFunctionsDeserializer implements JsonDeserializer<HashMap<String, AggregationFunctions>> {
    @Override
    public HashMap<String, AggregationFunctions> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        HashMap<String, AggregationFunctions> aggregationFunctionsMap = new HashMap<>();

        if (json.isJsonArray()) {
            // Handle case where aggregation_functions is an array of JSON objects
            JsonArray jsonArray = json.getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
                String key = jsonObject.get("function").getAsString();
                AggregationFunctions aggregationFunctions = context.deserialize(jsonObject, AggregationFunctions.class);
                aggregationFunctionsMap.put(key, aggregationFunctions);
            }
        } else {
            // Handle case where aggregation_functions is a single JSON object
            JsonObject jsonObject = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                AggregationFunctions aggregationFunctions = context.deserialize(value, AggregationFunctions.class);
                aggregationFunctionsMap.put(key, aggregationFunctions);
            }
        }

        return aggregationFunctionsMap;
    }
}
