/*******************************************************************************
 * Copyright (c) 2020, 2021 Red Hat, IBM Corporation and others.
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
package com.autotune.analyzer.deployment;

import com.autotune.analyzer.application.ApplicationDeployment;
import com.autotune.analyzer.application.ApplicationServiceStack;
import com.autotune.analyzer.application.Tunable;
import com.autotune.common.data.datasource.DataSource;
import com.autotune.common.data.datasource.DataSourceFactory;
import com.autotune.analyzer.exceptions.InvalidBoundsException;
import com.autotune.analyzer.exceptions.InvalidValueException;
import com.autotune.analyzer.exceptions.MonitoringAgentNotFoundException;
import com.autotune.analyzer.exceptions.MonitoringAgentNotSupportedException;
import com.autotune.analyzer.k8sObjects.*;
import com.autotune.common.data.experiments.DeploymentPolicy;
import com.autotune.common.data.experiments.DeploymentSettings;
import com.autotune.common.data.experiments.DeploymentTracking;
import com.autotune.common.data.experiments.TrialSettings;
import com.autotune.utils.AnalyzerConstants;
import com.autotune.utils.AnalyzerConstants.AutotuneConfigConstants;
import com.autotune.utils.AnalyzerErrorConstants;
import com.autotune.analyzer.variables.Variables;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.autotune.analyzer.Experimentator.startExperiment;
import static com.autotune.utils.AnalyzerConstants.POD_TEMPLATE_HASH;

/**
 * Maintains information about the Autotune resources deployed in the cluster
 */
public class AutotuneDeployment
{
	/**
	 * Key: Name of autotuneObject
	 * Value: AutotuneObject instance matching the name
	 */
	public static Map<String, AutotuneObject> autotuneObjectMap = new HashMap<>();
	public static Map<String, AutotuneConfig> autotuneConfigMap = new HashMap<>();

	/**
	 * Outer map:
	 * Key: Name of autotune Object
	 *
	 * Inner map:
	 * Key: Name of deployment
	 * Value: ApplicationDeployment instance matching the name
	 */
	public static Map <String, Map<String, ApplicationDeployment>> deploymentMap = new HashMap<>();

	private static final Logger LOGGER = LoggerFactory.getLogger(AutotuneDeployment.class);

	/**
	 * Get Autotune objects from kubernetes, and watch for any additions, modifications or deletions.
	 * Add obtained autotune objects to map and match autotune object with pods.
	 *
	 * @param autotuneDeployment
	 * @throws IOException if unable to get Kubernetes config
	 */
	public static void getAutotuneObjects(final AutotuneDeployment autotuneDeployment) throws IOException {
		KubernetesClient client = AutotuneDeploymentInfo.getKubernetesClient();

		/* Watch for events (additions, modifications or deletions) of autotune objects */
		Watcher<String> autotuneObjectWatcher = new Watcher<>() {
			@Override
			public void eventReceived(Action action, String resource) {
				AutotuneObject autotuneObject = null;

				switch (action.toString().toUpperCase()) {
					case "ADDED":
						autotuneObject = getAutotuneObject(resource);
						if (autotuneObject != null) {
							addAutotuneObject(autotuneObject, client);
							String autotuneObjectStr = autotuneObject.getExperimentName();
							// Each AutotuneObject can affect multiple applicationServiceStacks (micro services)
							// For each of these applicationServiceStacks, we need to start the experiments
							if (!deploymentMap.isEmpty() &&
									deploymentMap.get(autotuneObjectStr) != null) {
								Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObjectStr);
								for (String deploymentName : depMap.keySet()) {
									startExperiment(autotuneObject, depMap.get(deploymentName));
								}
								LOGGER.info("Added autotune object " + autotuneObject.getExperimentName());
							} else {
								LOGGER.error("autotune object " + autotuneObject.getExperimentName() + " not added as no related deployments found!");
							}
						}
						break;
					case "MODIFIED":
						autotuneObject = getAutotuneObject(resource);
						if (autotuneObject != null) {
							// Check if any of the values have changed from the existing object in the map
							if (autotuneObjectMap.get(autotuneObject.getExperimentName()).getExperimentId() != autotuneObject.getExperimentId()) {
								deleteExistingAutotuneObject(resource);
								addAutotuneObject(autotuneObject, client);

								String autotuneObjectStr = autotuneObject.getExperimentName();
								// Each AutotuneObject can affect multiple applicationServiceStacks (micro services)
								// For each of these applicationServiceStacks, we need to restart the experiments
								if (!deploymentMap.isEmpty() &&
										deploymentMap.get(autotuneObjectStr) != null) {
									Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObjectStr);
									for (String deploymentName : depMap.keySet()) {
										startExperiment(autotuneObject, depMap.get(deploymentName));
									}
									LOGGER.info("Updated autotune object " + autotuneObject.getExperimentName());
								} else {
									LOGGER.info("autotune object " + autotuneObject.getExperimentName() + " not updated!");
								}
							}
						}
						break;
					case "DELETED":
						deleteExistingAutotuneObject(resource);
					default:
						break;
				}
			}

			@Override
			public void onClose(KubernetesClientException e) { }
		};

		Watcher<String> autotuneConfigWatcher = new Watcher<>() {
			@Override
			public void eventReceived(Action action, String resource) {
				AutotuneConfig autotuneConfig = null;

				switch (action.toString().toUpperCase()) {
					case "ADDED":
						autotuneConfig = getAutotuneConfig(resource, client, KubernetesContexts.getAutotuneVariableContext());
						if (autotuneConfig != null) {
							autotuneConfigMap.put(autotuneConfig.getName(), autotuneConfig);
							LOGGER.info("Added autotuneconfig " + autotuneConfig.getName());
							addLayerInfo(autotuneConfig, null);
						}
						break;
					case "MODIFIED":
						autotuneConfig = getAutotuneConfig(resource, client, KubernetesContexts.getAutotuneVariableContext());
						if (autotuneConfig != null) {
							deleteExistingConfig(resource);
							autotuneConfigMap.put(autotuneConfig.getName(), autotuneConfig);
							LOGGER.info("Added modified autotuneconfig " + autotuneConfig.getName());
							addLayerInfo(autotuneConfig, null);
						}
						break;
					case "DELETED":
						deleteExistingConfig(resource);
					default:
						break;
				}
			}

			@Override
			public void onClose(KubernetesClientException e) { }
		};

		/* Register custom watcher for autotune object and autotuneconfig object*/
		client.customResource(KubernetesContexts.getAutotuneCrdContext()).watch(autotuneObjectWatcher);
		client.customResource(KubernetesContexts.getAutotuneConfigContext()).watch(autotuneConfigWatcher);
	}

	/**
	 * Add autotuneobject to monitoring map and match pods and autotuneconfigs
	 * @param autotuneObject
	 * @param client
	 */
	private static void addAutotuneObject(AutotuneObject autotuneObject, KubernetesClient client) {
		autotuneObjectMap.put(autotuneObject.getExperimentName(), autotuneObject);
		System.out.println("Autotune Object: " + autotuneObject.getExperimentName() + ": Finding Layers");

		matchPodsToAutotuneObject(autotuneObject, client);

		for (String autotuneConfig : autotuneConfigMap.keySet()) {
			addLayerInfo(autotuneConfigMap.get(autotuneConfig), autotuneObject);
		}
	}

	/**
	 * Delete autotuneobject that's currently monitored
	 * @param autotuneObject
	 */
	private static void deleteExistingAutotuneObject(String autotuneObject) {
		JSONObject autotuneObjectJson = new JSONObject(autotuneObject);
		String name = autotuneObjectJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.METADATA)
				.optString(AnalyzerConstants.AutotuneObjectConstants.NAME);

		autotuneObjectMap.remove(name);
		deploymentMap.remove(name);
		// TODO: Stop all the experiments
		LOGGER.info("Deleted autotune object {}", name);
	}

	/**
	 * Delete existing autotuneconfig in applications monitored by autotune
	 * @param resource JSON string of the autotuneconfig object
	 */
	private static void deleteExistingConfig(String resource) {
		JSONObject autotuneConfigJson = new JSONObject(resource);
		String configName = autotuneConfigJson.optString(AutotuneConfigConstants.LAYER_NAME);

		LOGGER.info("AutotuneConfig " + configName + " removed from autotune monitoring");
		// Remove from collection of autotuneconfigs in map
		autotuneConfigMap.remove(configName);

		// Remove autotuneconfig for all applications monitored
		for (String autotuneObjectKey : deploymentMap.keySet()) {
			Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObjectKey);
			for (String deploymentName : depMap.keySet()) {
				for (String applicationServiceStackName : depMap.get(deploymentName).getApplicationServiceStackMap().keySet()) {
					ApplicationServiceStack applicationServiceStack = depMap.get(deploymentName).getApplicationServiceStackMap().get(applicationServiceStackName);
					applicationServiceStack.getApplicationServiceStackLayers().remove(configName);
				}
			}
		}
	}

	/**
	 * Get map of pods matching the autotune object using the labels.
	 *
	 * @param autotuneObject
	 * @param client KubernetesClient to get pods in cluster
	 */
	private static void matchPodsToAutotuneObject(AutotuneObject autotuneObject, KubernetesClient client) {
		try {
			String userLabelKey = autotuneObject.getSelectorInfo().getMatchLabel();
			String userLabelValue = autotuneObject.getSelectorInfo().getMatchLabelValue();

			String namespace = autotuneObject.getNamespace();
			String experimentName = autotuneObject.getExperimentName();
			PodList podList = client.pods().inNamespace(namespace).withLabel(userLabelKey, userLabelValue).list();
			if (podList.getItems().isEmpty()) {
				LOGGER.error("autotune object " + autotuneObject.getExperimentName() + " not added as no related deployments found!");
				// TODO: No matching pods with the userLabelKey found, need to warn the user.
				return;
			}

			// We now have a list of pods. Get the stack (ie docker image) for each pod.
			// Add the unique set of stacks and create an ApplicationServiceStack object for each.
			for (Pod pod : podList.getItems()) {
				ObjectMeta podMetadata = pod.getMetadata();
				String podTemplateHash = podMetadata.getLabels().get(POD_TEMPLATE_HASH);
				String status = pod.getStatus().getPhase();
				// We want to find the deployment name for this pod.
				// To find that we first find the replicaset corresponding to the pod template hash
				// Replicaset name is of the form 'deploymentName-podTemplateHash'
				// So to get the deployment name we remove the '-podTemplateHash' from the Replicaset name
				ReplicaSetList replicaSetList = client.apps().replicaSets().inNamespace(namespace).withLabel(POD_TEMPLATE_HASH, podTemplateHash).list();
				if (replicaSetList.getItems().isEmpty()) {
					LOGGER.error("autotune object " + autotuneObject.getExperimentName() + " not added as no related deployments found!");
					// TODO: No matching pods with the userLabelKey found, need to warn the user.
					return;
				}
				String deploymentName = null;
				for (ReplicaSet replicaSet : replicaSetList.getItems()) {
					String replicasetName = replicaSet.getMetadata().getName();
					StringBuilder podHashSb = new StringBuilder("-").append(podTemplateHash);
					deploymentName = replicasetName.replace(podHashSb.toString(), "");
					Deployment deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
					LOGGER.debug("Pod: " + podMetadata.getName()
							+ " podTemplateHash: " + podTemplateHash
							+ " replicasetName: " + replicasetName
							+ " deploymentName: " + deploymentName);

					if (deployment != null) {
						// Add the deployment if it is already not there
						ApplicationDeployment applicationDeployment = null;
						if (!deploymentMap.containsKey(experimentName)) {
							applicationDeployment = new ApplicationDeployment(deploymentName,
									experimentName, namespace,
									deployment.getStatus().toString());
							Map<String, ApplicationDeployment> depMap = new HashMap<>();
							depMap.put(deploymentName, applicationDeployment);
							deploymentMap.put(experimentName, depMap);
						} else {
							applicationDeployment = deploymentMap.get(experimentName).get(deploymentName);
						}
						// Check docker image id for each container in the pod
						for (Container container : pod.getSpec().getContainers()) {
							String containerImageName = container.getImage();
							String containerName = container.getName();
							ApplicationServiceStack applicationServiceStack = new ApplicationServiceStack(containerImageName,
									containerName);
							assert(applicationDeployment == null);
							// Add the container image if it has not already been added to the deployment
							if (!applicationDeployment.getApplicationServiceStackMap().containsKey(containerImageName)) {
								applicationDeployment.getApplicationServiceStackMap().put(containerImageName, applicationServiceStack);
							}
						}
						break;
					}
				}
			}
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add Autotune object to map of monitored objects.
	 *
	 * @param autotuneObjectJsonStr JSON string of the autotune object
	 */
	private static AutotuneObject getAutotuneObject(String autotuneObjectJsonStr) {
		try {
			JSONObject autotuneObjectJson = new JSONObject(autotuneObjectJsonStr);
			JSONObject metadataJson = autotuneObjectJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.METADATA);

			String name;
			String mode;
			SloInfo sloInfo;
			String namespace;
			SelectorInfo selectorInfo;

			JSONObject specJson = autotuneObjectJson.optJSONObject(AnalyzerConstants.AutotuneObjectConstants.SPEC);

			JSONObject sloJson = null;
			String slo_class = null;
			String direction = null;
			String objectiveFunction = null;
			String hpoAlgoImpl = null;
			if (specJson != null) {
				sloJson = specJson.optJSONObject(AnalyzerConstants.AutotuneObjectConstants.SLO);
				slo_class = sloJson.optString(AnalyzerConstants.AutotuneObjectConstants.SLO_CLASS);
				direction = sloJson.optString(AnalyzerConstants.AutotuneObjectConstants.DIRECTION);
				hpoAlgoImpl = sloJson.optString(AnalyzerConstants.AutotuneObjectConstants.HPO_ALGO_IMPL);
				objectiveFunction = sloJson.optString(AnalyzerConstants.AutotuneObjectConstants.OBJECTIVE_FUNCTION);
			}

			JSONArray functionVariables = new JSONArray();
			if (sloJson != null) {
				functionVariables = sloJson.getJSONArray(AnalyzerConstants.AutotuneObjectConstants.FUNCTION_VARIABLES);
			}
			ArrayList<Metric> metricArrayList = new ArrayList<>();

			for (Object functionVariableObj : functionVariables) {
				JSONObject functionVariableJson = (JSONObject) functionVariableObj;
				String variableName = functionVariableJson.optString(AnalyzerConstants.AutotuneObjectConstants.NAME);
				String query = functionVariableJson.optString(AnalyzerConstants.AutotuneObjectConstants.QUERY);
				String datasource = functionVariableJson.optString(AnalyzerConstants.AutotuneObjectConstants.DATASOURCE);
				String valueType = functionVariableJson.optString(AnalyzerConstants.AutotuneObjectConstants.VALUE_TYPE);

				Metric metric = new Metric(variableName,
						query,
						datasource,
						valueType);

				metricArrayList.add(metric);
			}

			// If the user has not specified hpoAlgoImpl, we use the default one.
			if (hpoAlgoImpl == null || hpoAlgoImpl.isEmpty()) {
				hpoAlgoImpl = AnalyzerConstants.AutotuneObjectConstants.DEFAULT_HPO_ALGO_IMPL;
			}

			sloInfo = new SloInfo(slo_class,
					objectiveFunction,
					direction,
					hpoAlgoImpl,
					metricArrayList);

			JSONObject selectorJson = null;
			if (specJson != null) {
				selectorJson = specJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.SELECTOR);
			}

			assert selectorJson != null;
			String matchLabel = selectorJson.optString(AnalyzerConstants.AutotuneObjectConstants.MATCH_LABEL);
			String matchLabelValue = selectorJson.optString(AnalyzerConstants.AutotuneObjectConstants.MATCH_LABEL_VALUE);
			String matchRoute = selectorJson.optString(AnalyzerConstants.AutotuneObjectConstants.MATCH_ROUTE);
			String matchURI = selectorJson.optString(AnalyzerConstants.AutotuneObjectConstants.MATCH_URI);
			String matchService = selectorJson.optString(AnalyzerConstants.AutotuneObjectConstants.MATCH_SERVICE);

			selectorInfo = new SelectorInfo(matchLabel,
					matchLabelValue,
					matchRoute,
					matchURI,
					matchService);

			mode = specJson.optString(AnalyzerConstants.AutotuneObjectConstants.MODE);
			name = metadataJson.optString(AnalyzerConstants.AutotuneObjectConstants.NAME);
			namespace = metadataJson.optString(AnalyzerConstants.AutotuneObjectConstants.NAMESPACE);

			String resourceVersion = metadataJson.optString(AnalyzerConstants.RESOURCE_VERSION);
			String uid = metadataJson.optString(AnalyzerConstants.UID);
			String apiVersion = autotuneObjectJson.optString(AnalyzerConstants.API_VERSION);
			String kind = autotuneObjectJson.optString(AnalyzerConstants.KIND);

			ObjectReference objectReference = new ObjectReference(apiVersion,
					null,
					kind,
					name,
					namespace,
					resourceVersion,
					uid);

			JSONObject settingsJson = null;
			if (specJson != null) {
				settingsJson = specJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.SETTINGS);
			}

			AutotuneSettings autotuneSettings = getAutotuneSettingsData(settingsJson);

			return new AutotuneObject(name,
					namespace,
					mode,
					sloInfo,
					selectorInfo,
					objectReference,
					autotuneSettings);

		} catch (InvalidValueException | NullPointerException | JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Parse Autotune JSON and create object for AutotuneSettings
	 * @param settingsJson
	 * @return
	 */
	private static AutotuneSettings getAutotuneSettingsData(JSONObject settingsJson) {
		String measurementCycles = settingsJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.TRIAL_SETTINGS)
				.optString(AnalyzerConstants.AutotuneObjectConstants.MEASUREMENT_CYCLES);
		String warmupDuration = settingsJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.TRIAL_SETTINGS)
				.optString(AnalyzerConstants.AutotuneObjectConstants.WARMUP_DURATION);
		String warmupCycles = settingsJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.TRIAL_SETTINGS)
				.optString(AnalyzerConstants.AutotuneObjectConstants.WARMUP_CYCLES);
		String measurementDuration = settingsJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.TRIAL_SETTINGS)
				.optString(AnalyzerConstants.AutotuneObjectConstants.MEASUREMENT_DURATION);
		String trialIterations = settingsJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.TRIAL_SETTINGS)
				.optString(AnalyzerConstants.AutotuneObjectConstants.TRIAL_ITERATIONS);

		TrialSettings trialSettings = new TrialSettings(trialIterations,
				warmupDuration,
				warmupCycles,
				measurementDuration,
				measurementCycles
		);

		String deployment_policy_type = settingsJson
				.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.DEPLOYMENT_POLICY)
				.optString(AnalyzerConstants.AutotuneObjectConstants.DEPLOYMENT_POLICY_TYPE);

		DeploymentPolicy deploymentPolicy = new DeploymentPolicy(deployment_policy_type);

		ArrayList<String> trackers = new ArrayList<>();
		JSONObject deploymentSettingsJson = settingsJson.optJSONObject(AnalyzerConstants.AutotuneObjectConstants.DEPLOYMENT_SETTINGS);
		JSONArray deploymentTrackingArr = new JSONArray();
		if (deploymentSettingsJson != null) {
			deploymentTrackingArr = deploymentSettingsJson.getJSONArray(AnalyzerConstants.AutotuneObjectConstants.DEPLOYMENT_TRACKING);
		}

		for (Object deploymentSettingsObj : deploymentTrackingArr) {
			JSONObject deploymentTrackingJson = (JSONObject) deploymentSettingsObj;
			String variableName = deploymentTrackingJson.optString(AnalyzerConstants.AutotuneObjectConstants.NAME);

			trackers.add(deploymentTrackingJson.optString(AnalyzerConstants.AutotuneObjectConstants.TRACKERS));
		}
		DeploymentTracking deploymentTracking = new DeploymentTracking(trackers);
		DeploymentSettings deploymentSettings = new DeploymentSettings(deploymentPolicy, deploymentTracking);

		return new AutotuneSettings(trialSettings,deploymentSettings,deploymentPolicy);
	}

	/**
	 * Parse AutotuneConfig JSON and create matching AutotuneConfig object
	 *
	 * @param autotuneConfigResource  The JSON file for the autotuneconfig resource in the cluster.
	 * @param client
	 * @param autotuneVariableContext
	 */
	@SuppressWarnings("unchecked")
	private static AutotuneConfig getAutotuneConfig(String autotuneConfigResource, KubernetesClient client, CustomResourceDefinitionContext autotuneVariableContext) {
		try {
			JSONObject autotuneConfigJson = new JSONObject(autotuneConfigResource);
			JSONObject metadataJson = autotuneConfigJson.getJSONObject(AnalyzerConstants.AutotuneObjectConstants.METADATA);
			JSONObject presenceJson = autotuneConfigJson.optJSONObject(AutotuneConfigConstants.LAYER_PRESENCE);

			String presence = null;
			JSONArray layerPresenceQueryJson = null;
			JSONArray layerPresenceLabelJson = null;
			if (presenceJson != null) {
				presence = presenceJson.optString(AnalyzerConstants.AutotuneConfigConstants.PRESENCE);
				layerPresenceQueryJson = presenceJson.optJSONArray(AnalyzerConstants.AutotuneConfigConstants.QUERIES);
				layerPresenceLabelJson = presenceJson.optJSONArray(AnalyzerConstants.AutotuneConfigConstants.LABEL);
			}

			String name = autotuneConfigJson.getJSONObject(AutotuneConfigConstants.METADATA).optString(AutotuneConfigConstants.NAME);
			String namespace = autotuneConfigJson.getJSONObject(AutotuneConfigConstants.METADATA).optString(AutotuneConfigConstants.NAMESPACE);

			// Get the autotunequeryvariables for the current kubernetes environment
			ArrayList<Map<String, String>> queryVarList = null;
			try {
				Map<String, Object> envVariblesMap = client.customResource(autotuneVariableContext).get(namespace, AutotuneDeploymentInfo.getKubernetesType());
				queryVarList = (ArrayList<Map<String, String>>) envVariblesMap.get(AnalyzerConstants.AutotuneConfigConstants.QUERY_VARIABLES);
			} catch (Exception e) {
				LOGGER.error("Autotunequeryvariable and autotuneconfig {} not in the same namespace", name);
				return null;
			}

			String layerPresenceQueryStr = null;
			String layerPresenceKey = null;

			ArrayList<LayerPresenceQuery> layerPresenceQueries = new ArrayList<>();
			if (layerPresenceQueryJson != null) {
				for (Object query : layerPresenceQueryJson) {
					JSONObject queryJson = (JSONObject) query;
					String datasource = queryJson.getString(AnalyzerConstants.AutotuneConfigConstants.DATASOURCE);
					if (datasource.equalsIgnoreCase(AutotuneDeploymentInfo.getMonitoringAgent())) {
						layerPresenceQueryStr = queryJson.getString(AnalyzerConstants.AutotuneConfigConstants.QUERY);
						layerPresenceKey = queryJson.getString(AnalyzerConstants.AutotuneConfigConstants.KEY);
						// Replace the queryvariables in the query
						try {
							layerPresenceQueryStr = Variables.updateQueryWithVariables(null, null,
									layerPresenceQueryStr, queryVarList);
							LayerPresenceQuery layerPresenceQuery = new LayerPresenceQuery(datasource, layerPresenceQueryStr, layerPresenceKey);
							layerPresenceQueries.add(layerPresenceQuery);
						} catch (IOException | MonitoringAgentNotSupportedException e) {
							LOGGER.error("autotuneconfig {}: Unsupported Datasource: {}", name, datasource);
							return null;
						}
					}
				}
			}

			String layerPresenceLabel = null;
			String layerPresenceLabelValue = null;
			if (layerPresenceLabelJson != null) {
				for (Object label : layerPresenceLabelJson) {
					JSONObject labelJson = (JSONObject) label;
					layerPresenceLabel = labelJson.optString(AutotuneConfigConstants.NAME);
					layerPresenceLabelValue = labelJson.optString(AutotuneConfigConstants.VALUE);
				}
			}

			String layerName = autotuneConfigJson.optString(AnalyzerConstants.AutotuneConfigConstants.LAYER_NAME);
			String details = autotuneConfigJson.optString(AnalyzerConstants.AutotuneConfigConstants.DETAILS);
			int level = autotuneConfigJson.optInt(AnalyzerConstants.AutotuneConfigConstants.LAYER_LEVEL);
			JSONArray tunablesJsonArray = autotuneConfigJson.optJSONArray(AnalyzerConstants.AutotuneConfigConstants.TUNABLES);
			ArrayList<Tunable> tunableArrayList = new ArrayList<>();

			for (Object tunablesObject : tunablesJsonArray) {
				JSONObject tunableJson = (JSONObject) tunablesObject;
				JSONArray tunableQueriesArray = tunableJson.optJSONArray(AnalyzerConstants.AutotuneConfigConstants.QUERIES);

				// Store the datasource and query from the JSON in a map
				Map<String, String> queriesMap = new HashMap<>();
				if (tunableQueriesArray != null) {
					for (Object tunableQuery : tunableQueriesArray) {
						JSONObject tunableQueryObj = (JSONObject) tunableQuery;
						String datasource = tunableQueryObj.optString(AnalyzerConstants.AutotuneConfigConstants.DATASOURCE);
						String datasourceQuery = tunableQueryObj.optString(AnalyzerConstants.AutotuneConfigConstants.QUERY);
						queriesMap.put(datasource, datasourceQuery);
					}
				}

				String tunableName = tunableJson.optString(AutotuneConfigConstants.NAME);
				String tunableValueType = tunableJson.optString(AutotuneConfigConstants.VALUE_TYPE);
				String upperBound = tunableJson.optString(AutotuneConfigConstants.UPPER_BOUND);
				String lowerBound = tunableJson.optString(AutotuneConfigConstants.LOWER_BOUND);
				// Read in step from the tunable, set it to '1' if not specified.
				double step = tunableJson.optDouble(AutotuneConfigConstants.STEP, 1);

				ArrayList<String> sloClassList = new ArrayList<>();
				JSONArray sloClassJson = tunableJson.getJSONArray(AnalyzerConstants.AutotuneConfigConstants.SLO_CLASS);

				for (Object sloClassObject : sloClassJson) {
					String sloClass = (String) sloClassObject;
					sloClassList.add(sloClass);
				}

				Tunable tunable;
				try {
					tunable = new Tunable(tunableName, step, upperBound, lowerBound, tunableValueType, queriesMap, sloClassList, layerName);
					tunableArrayList.add(tunable);
				} catch (InvalidBoundsException e) {
					e.printStackTrace();
				}
			}

			String resourceVersion = metadataJson.optString(AnalyzerConstants.RESOURCE_VERSION);
			String uid = metadataJson.optString(AnalyzerConstants.UID);
			String apiVersion = autotuneConfigJson.optString(AnalyzerConstants.API_VERSION);
			String kind = autotuneConfigJson.optString(AnalyzerConstants.KIND);

			ObjectReference objectReference = new ObjectReference(apiVersion,
					null,
					kind,
					name,
					namespace,
					resourceVersion,
					uid);

			return new AutotuneConfig(name,
					layerName,
					level,
					details,
					presence,
					layerPresenceQueries,
					layerPresenceLabel,
					layerPresenceLabelValue,
					tunableArrayList,
					objectReference);
		} catch (JSONException | InvalidValueException | NullPointerException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * This method adds the default (container) layer to all the monitored applications in the cluster
	 * If the autotuneObject is not null, then it adds the default layer only to stacks associated with that object.
	 *
	 * @param layer
	 * @param autotuneObject
	 */
	private static void addDefaultLayer(AutotuneConfig layer, AutotuneObject autotuneObject)  {
		String presence = layer.getPresence();
		// Add to all monitored applications in the cluster
		if (presence.equals(AnalyzerConstants.PRESENCE_ALWAYS)) {
			if (autotuneObject == null) {
				for (String autotuneObjectKey : deploymentMap.keySet()) {
					Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObjectKey);
					for (String deploymentName : depMap.keySet()) {
						for (String containerImageName : depMap.get(deploymentName).getApplicationServiceStackMap().keySet()) {
							ApplicationServiceStack applicationServiceStack = depMap.get(deploymentName).getApplicationServiceStackMap().get(containerImageName);
							addLayerInfoToApplication(applicationServiceStack, layer);
						}
					}
				}
			} else {
				Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObject.getExperimentName());
				for (String deploymentName : depMap.keySet()) {
					for (String containerImageName : depMap.get(deploymentName).getApplicationServiceStackMap().keySet()) {
						ApplicationServiceStack applicationServiceStack = depMap.get(deploymentName).getApplicationServiceStackMap().get(containerImageName);
						addLayerInfoToApplication(applicationServiceStack, layer);
					}
				}
			}
		}
	}

	/**
	 * Check if a layer has a datasource query that validates its presence
	 *
	 * @param layer
	 * @param autotuneObject
	 */
	private static void addQueryLayer(AutotuneConfig layer, AutotuneObject autotuneObject)  {
		try {
			// TODO: This query needs to be optimized to only check for pods in the right namespace
			KubernetesClient client = new DefaultKubernetesClient();
			PodList podList = null;
			if (autotuneObject != null) {
				podList = client.pods().inNamespace(autotuneObject.getNamespace()).list();
			} else {
				podList = client.pods().inAnyNamespace().list();
			}
			if (podList == null) {
				LOGGER.error(AnalyzerErrorConstants.AutotuneConfigErrors.COULD_NOT_GET_LIST_OF_APPLICATIONS + layer.getName());
				return;
			}
			DataSource autotuneDataSource = null;
			try {
				autotuneDataSource = DataSourceFactory.getDataSource(AutotuneDeploymentInfo.getMonitoringAgent());
			} catch (MonitoringAgentNotFoundException e) {
				e.printStackTrace();
			}
			ArrayList<String> appsForAllQueries = new ArrayList<>();
			ArrayList<LayerPresenceQuery> layerPresenceQueries = layer.getLayerPresenceQueries();
			// Check if a layer has a datasource query that validates its presence
			if (layerPresenceQueries != null && !layerPresenceQueries.isEmpty()) {
				for (LayerPresenceQuery layerPresenceQuery : layerPresenceQueries) {
					try {
						// TODO: Check the datasource in the query is the same as the Autotune one
						ArrayList<String> apps = (ArrayList<String>) autotuneDataSource.getAppsForLayer(layerPresenceQuery.getLayerPresenceQuery(),
								layerPresenceQuery.getLayerPresenceKey());
						appsForAllQueries.addAll(apps);
					} catch (MalformedURLException | NullPointerException e) {
						LOGGER.error(AnalyzerErrorConstants.AutotuneConfigErrors.COULD_NOT_GET_LIST_OF_APPLICATIONS + layer.getName());
					}
				}
				// We now have a list of apps that have the label and the key specified by the user.
				// We now have to find the kubernetes objects corresponding to these apps
				if (!appsForAllQueries.isEmpty()) {
					for (String application : appsForAllQueries) {
						List<Container> containers = null;
						for (Pod pod : podList.getItems()) {
							if (pod.getMetadata().getName().contains(application)) {
								// We found a POD that matches the app name, now get its containers
								containers = pod.getSpec().getContainers();
								break;
							}
						}
						// No containers were found that matched the applications, this is weird, log a warning
						if (containers == null) {
							LOGGER.warn("Could not find any PODs related to Application name: " + application);
							continue;
						}
						for (Container container : containers) {
							String containerImageName = container.getImage();
							// Check if the container image is already present in the applicationServiceStackMap, if not, add it
							if (autotuneObject != null) {
								Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObject.getExperimentName());
								for (String deploymentName : depMap.keySet()) {
									if (depMap.get(deploymentName).getApplicationServiceStackMap().containsKey(containerImageName)) {
										addLayerInfoToApplication(depMap.get(deploymentName).getApplicationServiceStackMap().get(containerImageName), layer);
									}
								}
							} else {
								for (String autotuneObjectKey : deploymentMap.keySet()) {
									Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObjectKey);
									for (String deploymentName : depMap.keySet()) {
										if (depMap.get(deploymentName).getApplicationServiceStackMap().containsKey(containerImageName)) {
											addLayerInfoToApplication(depMap.get(deploymentName).getApplicationServiceStackMap().get(containerImageName), layer);
										}
									}
								}
							}
						}
					}
				} else {
					LOGGER.error(AnalyzerErrorConstants.AutotuneConfigErrors.COULD_NOT_GET_LIST_OF_APPLICATIONS + layer.getName());
				}
			} else {
				LOGGER.error(AnalyzerErrorConstants.AutotuneConfigErrors.COULD_NOT_GET_LIST_OF_APPLICATIONS + layer.getName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Attach a newly added to the relevant stacks
	 * If the autotuneObject is null, try to find all the relevant stacks
	 * under observation currently and add the new layer.
	 *
	 * @param layer
	 * @param autotuneObject
	 */
	private static void addLayerInfo(AutotuneConfig layer, AutotuneObject autotuneObject) {
		// Add the default layer for all monitored pods
		addDefaultLayer(layer, autotuneObject);

		// Match layer presence queries if any
		addQueryLayer(layer, autotuneObject);

		try {
			String layerPresenceLabel = layer.getLayerPresenceLabel();
			String layerPresenceLabelValue = layer.getLayerPresenceLabelValue();
			if (layerPresenceLabel != null) {
				KubernetesClient client = new DefaultKubernetesClient();
				PodList podList = null;
				if (autotuneObject != null) {
					podList = client.pods().inNamespace(autotuneObject.getNamespace()).withLabel(layerPresenceLabel, layerPresenceLabelValue).list();
				} else {
					podList = client.pods().inAnyNamespace().withLabel(layerPresenceLabel, layerPresenceLabelValue).list();
				}

				if (podList.getItems().isEmpty()) {
					LOGGER.error(AnalyzerErrorConstants.AutotuneConfigErrors.COULD_NOT_GET_LIST_OF_APPLICATIONS + layer.getName());
					return;
				}
				for (Pod pod : podList.getItems()) {
					for (Container container : pod.getSpec().getContainers()) {
						String containerImageName = container.getImage();
						if (autotuneObject != null) {
							Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObject.getExperimentName());
							for (String deploymentName : depMap.keySet()) {
								if (depMap.get(deploymentName).getApplicationServiceStackMap().containsKey(containerImageName)) {
									addLayerInfoToApplication(depMap.get(deploymentName).getApplicationServiceStackMap().get(containerImageName), layer);
								}
							}
						} else {
							for (String autotuneObjectKey : deploymentMap.keySet()) {
								Map<String, ApplicationDeployment> depMap = deploymentMap.get(autotuneObjectKey);
								for (String deploymentName : depMap.keySet()) {
									if (depMap.get(deploymentName).getApplicationServiceStackMap().containsKey(containerImageName)) {
										addLayerInfoToApplication(depMap.get(deploymentName).getApplicationServiceStackMap().get(containerImageName), layer);
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add layer, queries and tunables info to the autotuneObject
	 *
	 * @param applicationServiceStack ApplicationServiceStack instance that contains the layer
	 * @param autotuneConfig          AutotuneConfig object for the layer
	 */
	private static void addLayerInfoToApplication(ApplicationServiceStack applicationServiceStack, AutotuneConfig autotuneConfig) {
		// Check if layer already exists
		if (!applicationServiceStack.getApplicationServiceStackLayers().isEmpty() &&
				applicationServiceStack.getApplicationServiceStackLayers().containsKey(autotuneConfig.getName())) {
			return;
		}

		ArrayList<Tunable> tunables = new ArrayList<>();
		for (Tunable tunable : autotuneConfig.getTunables()) {
			try {
				Map<String, String> queries = new HashMap<>(tunable.getQueries());

				Tunable tunableCopy = new Tunable(tunable.getName(),
						tunable.getStep(),
						tunable.getUpperBound(),
						tunable.getLowerBound(),
						tunable.getValueType(),
						queries,
						tunable.getSloClassList(),
						tunable.getLayerName());
				tunables.add(tunableCopy);
			} catch (InvalidBoundsException ignored) { }
		}

		// Create autotuneconfigcopy with updated tunables arraylist
		AutotuneConfig autotuneConfigCopy = null;
		try {
			autotuneConfigCopy = new AutotuneConfig(
					autotuneConfig.getName(),
					autotuneConfig.getLayerName(),
					autotuneConfig.getLevel(),
					autotuneConfig.getDetails(),
					autotuneConfig.getPresence(),
					autotuneConfig.getLayerPresenceQueries(),
					autotuneConfig.getLayerPresenceLabel(),
					autotuneConfig.getLayerPresenceLabelValue(),
					tunables,
					autotuneConfig.getObjectReference());
		} catch (InvalidValueException ignored) { }

		LOGGER.info("Added layer " + autotuneConfig.getName() + " to stack " + applicationServiceStack.getStackName());
		applicationServiceStack.getApplicationServiceStackLayers().put(autotuneConfig.getName(), autotuneConfigCopy);
	}
}
