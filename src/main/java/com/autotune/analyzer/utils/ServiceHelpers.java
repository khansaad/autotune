/*******************************************************************************
 * Copyright (c) 2021, 2021 Red Hat, IBM Corporation and others.
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

import com.autotune.analyzer.application.ApplicationServiceStack;
import com.autotune.analyzer.application.Tunable;
import com.autotune.analyzer.datasource.DataSource;
import com.autotune.analyzer.datasource.DataSourceFactory;
import com.autotune.analyzer.deployment.DeploymentInfo;
import com.autotune.analyzer.exceptions.MonitoringAgentNotFoundException;
import com.autotune.analyzer.k8sObjects.AutotuneConfig;
import com.autotune.analyzer.k8sObjects.AutotuneObject;
import com.autotune.analyzer.k8sObjects.FunctionVariable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.autotune.analyzer.deployment.AutotuneDeployment.applicationServiceStackMap;

/**
 * Helper functions used by the REST APIs to create the output JSON object
 */
public class ServiceHelpers {
	private ServiceHelpers() { }

	/**
	 * Copy over the details of the experiment from the given Autotune Object to the JSON object provided.
	 *
	 * @param experimentJson
	 * @param autotuneObject
	 */
	public static void addExperimentDetails(JSONObject experimentJson, AutotuneObject autotuneObject) {
		experimentJson.put(AnalyzerConstants.ServiceConstants.EXPERIMENT_NAME, autotuneObject.getExperimentName());
		experimentJson.put(AnalyzerConstants.AutotuneObjectConstants.DIRECTION, autotuneObject.getSloInfo().getDirection());
		experimentJson.put(AnalyzerConstants.AutotuneObjectConstants.OBJECTIVE_FUNCTION, autotuneObject.getSloInfo().getObjectiveFunction());
		experimentJson.put(AnalyzerConstants.AutotuneObjectConstants.SLO_CLASS, autotuneObject.getSloInfo().getSloClass());
		experimentJson.put(AnalyzerConstants.AutotuneObjectConstants.EXPERIMENT_ID, autotuneObject.getExperimentId());
		experimentJson.put(AnalyzerConstants.AutotuneObjectConstants.HPO_ALGO_IMPL, autotuneObject.getSloInfo().getHpoAlgoImpl());
	}

	/**
	 * Copy over the array of stack names for the given Autotune Object to the JSON Object provided
	 *
	 * @param stackJson
	 * @param autotuneObject
	 */
	public static void addStackDetails(JSONObject stackJson, AutotuneObject autotuneObject) {
		Set<String> applicationServiceStack = applicationServiceStackMap.get(autotuneObject.getExperimentName()).keySet();
		if (applicationServiceStack == null) {
			return;
		}

		JSONArray stackArray = new JSONArray();
		for (String containerImageName : applicationServiceStackMap.get(autotuneObject.getExperimentName()).keySet()) {
			stackArray.put(containerImageName);
		}
		stackJson.put(AnalyzerConstants.ServiceConstants.STACKS, stackArray);
	}

	/**
	 * Copy over the details of the LAYER from the given AutotuneConfig object to the JSON object provided
	 *
	 * @param layerJson
	 * @param autotuneConfig
	 */
	public static void addLayerDetails(JSONObject layerJson, AutotuneConfig autotuneConfig) {
		layerJson.put(AnalyzerConstants.AutotuneConfigConstants.LAYER_ID, autotuneConfig.getLayerId());
		layerJson.put(AnalyzerConstants.AutotuneConfigConstants.LAYER_NAME, autotuneConfig.getLayerName());
		layerJson.put(AnalyzerConstants.ServiceConstants.LAYER_DETAILS, autotuneConfig.getDetails());
		layerJson.put(AnalyzerConstants.AutotuneConfigConstants.LAYER_LEVEL, autotuneConfig.getLevel());
	}

	/**
	 * Copy over the tunable details of the TUNABLE provided without adding the query details
	 *
	 * @param tunableJson
	 * @param tunable
	 */
	private static void addTunable(JSONObject tunableJson, Tunable tunable) {
		tunableJson.put(AnalyzerConstants.AutotuneConfigConstants.NAME, tunable.getName());
		tunableJson.put(AnalyzerConstants.AutotuneConfigConstants.UPPER_BOUND, tunable.getUpperBound());
		tunableJson.put(AnalyzerConstants.AutotuneConfigConstants.LOWER_BOUND, tunable.getLowerBound());
		tunableJson.put(AnalyzerConstants.AutotuneConfigConstants.VALUE_TYPE, tunable.getValueType());
		tunableJson.put(AnalyzerConstants.AutotuneConfigConstants.STEP, tunable.getStep());
	}

	/**
	 * Copy over the details of the TUNABLES of a LAYER from the given AutotuneConfig object to the JSON object provided
	 * If the sloClass is not null then only copy over the TUNABLE if it matches the sloClass.
	 *
	 * @param tunablesArray
	 * @param autotuneConfig
	 * @param sloClass
	 */
	public static void addLayerTunableDetails(JSONArray tunablesArray, AutotuneConfig autotuneConfig, String sloClass) {
		for (Tunable tunable : autotuneConfig.getTunables()) {
			if (sloClass == null || tunable.sloClassList.contains(sloClass)) {
				JSONObject tunableJson = new JSONObject();
				addTunable(tunableJson, tunable);
				String tunableQuery = tunable.getQueries().get(DeploymentInfo.getMonitoringAgent());
				String query = AnalyzerConstants.NONE;
				if (tunableQuery != null && !tunableQuery.isEmpty()) {
					query = tunableQuery;
				}
				tunableJson.put(AnalyzerConstants.ServiceConstants.QUERY_URL, query);

				tunablesArray.put(tunableJson);
			}
		}
	}

	/**
	 * Copy over the details of the user specified function variables from the given autotune object to the JSON object provided
	 *
	 * @param funcVarJson
	 * @param autotuneObject
	 */
	public static void addFunctionVariablesDetails(JSONObject funcVarJson, AutotuneObject autotuneObject) {
		// Add function_variables info
		JSONArray functionVariablesArray = new JSONArray();
		for (FunctionVariable functionVariable : autotuneObject.getSloInfo().getFunctionVariables()) {
			JSONObject functionVariableJson = new JSONObject();
			functionVariableJson.put(AnalyzerConstants.AutotuneObjectConstants.NAME, functionVariable.getName());
			functionVariableJson.put(AnalyzerConstants.AutotuneObjectConstants.VALUE_TYPE, functionVariable.getValueType());
			functionVariableJson.put(AnalyzerConstants.ServiceConstants.QUERY_URL, functionVariable.getQuery());

			functionVariablesArray.put(functionVariableJson);
		}
		funcVarJson.put(AnalyzerConstants.AutotuneObjectConstants.FUNCTION_VARIABLES, functionVariablesArray);
	}

	/**
	 * Copy over the details of the SearchSpace from the given Autotune object to the JSON object provided.
	 * The searchSpace will be specific to a pod as provided.
	 *
	 * @param outputJsonArray
	 * @param autotuneObjectKey
	 * @param autotuneObject
	 * @param containerImageName
	 */
	public static void addApplicationToSearchSpace(JSONArray outputJsonArray, String autotuneObjectKey, AutotuneObject autotuneObject, String containerImageName) {
		ApplicationServiceStack applicationServiceStack = applicationServiceStackMap
				.get(autotuneObjectKey).get(containerImageName);

		if (applicationServiceStack == null) {
			return;
		}

		JSONObject applicationJson = new JSONObject();
		applicationJson.put(AnalyzerConstants.ServiceConstants.EXPERIMENT_NAME, autotuneObject.getExperimentName());
		applicationJson.put(AnalyzerConstants.AutotuneObjectConstants.DIRECTION, autotuneObject.getSloInfo().getDirection());
		applicationJson.put(AnalyzerConstants.AutotuneObjectConstants.OBJECTIVE_FUNCTION, autotuneObject.getSloInfo().getObjectiveFunction());
		applicationJson.put(AnalyzerConstants.AutotuneObjectConstants.EXPERIMENT_ID, autotuneObject.getExperimentId());
		applicationJson.put(AnalyzerConstants.AutotuneObjectConstants.HPO_ALGO_IMPL, autotuneObject.getSloInfo().getHpoAlgoImpl());

		JSONArray tunablesJsonArray = new JSONArray();
		for (String autotuneConfigName : applicationServiceStack.getApplicationServiceStackLayers().keySet()) {
			AutotuneConfig autotuneConfig = applicationServiceStack.getApplicationServiceStackLayers().get(autotuneConfigName);
			for (Tunable tunable : autotuneConfig.getTunables()) {
				JSONObject tunableJson = new JSONObject();
				addTunable(tunableJson, tunable);
				tunablesJsonArray.put(tunableJson);
			}
		}

		applicationJson.put(AnalyzerConstants.AutotuneConfigConstants.TUNABLES, tunablesJsonArray);
		outputJsonArray.put(applicationJson);
	}
}
