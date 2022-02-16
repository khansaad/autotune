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
package com.autotune.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Supported types to both Autotune and AutotuneConfig objects
 */
public class AutotuneSupportedTypes
{
	private AutotuneSupportedTypes() { }

	public static final Set<String> DIRECTIONS_SUPPORTED =
			new HashSet<>(Arrays.asList("minimize", "maximize"));

	public static final Set<String> MONITORING_AGENTS_SUPPORTED =
			new HashSet<>(Arrays.asList("prometheus"));

	public static final Set<String> MODES_SUPPORTED =
			new HashSet<>(Arrays.asList("show", "try", "apply"));

	public static final Set<String> PRESENCE_SUPPORTED =
			new HashSet<>(Arrays.asList("always", "", null));

	public static final Set<String> SLO_CLASSES_SUPPORTED =
			new HashSet<>(Arrays.asList("throughput", "response_time", "resource_usage"));

	public static final Set<String> LAYERS_SUPPORTED =
			new HashSet<>(Arrays.asList("container", "hotspot", "quarkus"));

	public static final Set<String> VALUE_TYPES_SUPPORTED =
			new HashSet<>(Arrays.asList("double", "int", "string", "categorical"));

	public static final Set<String> CLUSTER_TYPES_SUPPORTED =
			new HashSet<>(Arrays.asList("kubernetes"));

	public static final Set<String> K8S_TYPES_SUPPORTED =
			new HashSet<>(Arrays.asList("minikube", "openshift", "icp", null));

	public static final Set<String> AUTH_TYPES_SUPPORTED =
			new HashSet<>(Arrays.asList("saml", "oidc", "", null));

	public static final Set<String> LOGGING_TYPES_SUPPORTED =
			new HashSet<>(Arrays.asList("all", "debug", "error", "info", "off", "warn"));

	public static final Set<String> HPO_ALGOS_SUPPORTED =
			new HashSet<>(Arrays.asList("optuna_tpe", "optuna_tpe_multivariate", "optuna_skopt", null));
}
