/*******************************************************************************
 * Copyright (c) 2022 Red Hat, IBM Corporation and others.
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

import com.autotune.common.data.result.Recommendation;
import com.autotune.common.data.result.RecommendationConfigItem;
import com.autotune.common.data.result.StartEndTimeStampResults;
import com.autotune.common.k8sObjects.ContainerObject;
import com.autotune.common.k8sObjects.DeploymentObject;
import com.autotune.common.k8sObjects.KruizeObject;
import com.autotune.utils.AnalyzerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO Aggregate Function should be inline with perf profile class
 */

public class GenerateRecommendation {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateRecommendation.class);

    private static Map<String, Integer> recommendation_periods = new HashMap<>();


    public static void generateRecommendation(KruizeObject kruizeObject) {
        try {
            recommendation_periods.put("Short Term", 1);
            recommendation_periods.put("Middle Term", 7);
            recommendation_periods.put("Long Term", 15);

            for (String dName : kruizeObject.getDeployments().keySet()) {
                DeploymentObject deploymentObj = kruizeObject.getDeployments().get(dName);
                for (String cName : deploymentObj.getContainers().keySet()) {
                    System.out.println(cName);
                    ContainerObject containerObject = deploymentObj.getContainers().get(cName);
                    Timestamp monitorEndDate = containerObject.getResults().keySet().stream().max(Timestamp::compareTo).get();
                    Timestamp minDate = containerObject.getResults().keySet().stream().min(Timestamp::compareTo).get();
                    Timestamp monitorStartDate;
                    HashMap<String, Recommendation> recommendationPeriodMap = new HashMap<>();
                    for (String recPeriod : recommendation_periods.keySet()) {
                        int days = recommendation_periods.get(recPeriod);
                        monitorStartDate = addDays(monitorEndDate, -1 * days);
                        if (monitorStartDate.compareTo(minDate) >= 0 || days == 1) {
                            Timestamp finalMonitorStartDate = monitorStartDate;
                            System.out.println(finalMonitorStartDate);
                            System.out.println(monitorEndDate);
                            Map<Timestamp, StartEndTimeStampResults> filteredResultsMap = containerObject.getResults().entrySet().stream()
                                    .filter((x -> ((x.getKey().compareTo(finalMonitorStartDate) >= 0)
                                            && (x.getKey().compareTo(monitorEndDate) <= 0))))
                                    .collect((Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                            System.out.println(filteredResultsMap);
                            Recommendation recommendation = new Recommendation(monitorStartDate, monitorEndDate);
                            HashMap<AnalyzerConstants.CapacityMax, HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem>> config = new HashMap<>();
                            HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> capacityMap = new HashMap<>();
                            capacityMap.put(AnalyzerConstants.RecommendationItem.cpu, getCPUCapacityRecommendation(filteredResultsMap));
                            capacityMap.put(AnalyzerConstants.RecommendationItem.memory, getMemoryCapacityRecommendation(filteredResultsMap));
                            config.put(AnalyzerConstants.CapacityMax.capacity, capacityMap);
                            HashMap<AnalyzerConstants.RecommendationItem, RecommendationConfigItem> maxMap = new HashMap<>();
                            maxMap.put(AnalyzerConstants.RecommendationItem.cpu, getCPUMaxRecommendation(filteredResultsMap));
                            maxMap.put(AnalyzerConstants.RecommendationItem.memory, getMemoryMaxRecommendation(filteredResultsMap));
                            config.put(AnalyzerConstants.CapacityMax.max, maxMap);
                            Double hours = filteredResultsMap.values().stream().map((x) -> (x.getDurationInMinutes()))
                                    .collect(Collectors.toList())
                                    .stream()
                                    .mapToDouble(f -> f.doubleValue()).sum() / 60;
                            recommendation.setDuration_in_hours(hours);
                            recommendation.setConfig(config);
                            recommendationPeriodMap.put(recPeriod, recommendation);
                        } else {
                            recommendationPeriodMap.put(recPeriod, new Recommendation("There is not enough data available to generate a recommendation."));
                        }
                    }
                    HashMap<Timestamp, HashMap<String, Recommendation>> containerRecommendationMap = containerObject.getRecommendation();
                    if (null == containerRecommendationMap)
                        containerRecommendationMap = new HashMap<>();
                    containerRecommendationMap.put(monitorEndDate, recommendationPeriodMap);
                    containerObject.setRecommendation(containerRecommendationMap);
                    System.out.println(containerRecommendationMap);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Unable to get recommendation for : {} due to {}", kruizeObject.getExperimentName(), e.getMessage());
        }
    }

    private static RecommendationConfigItem getCPUCapacityRecommendation(Map<Timestamp, StartEndTimeStampResults> filteredResultsMap) {
        RecommendationConfigItem recommendationConfigItem = null;
        try {
            List<Double> doubleList = filteredResultsMap.values()
                    .stream()
                    .map(e -> e.getMetrics().get(AnalyzerConstants.AggregatorType.cpuUsage).getSum() + e.getMetrics().get(AnalyzerConstants.AggregatorType.cpuThrottle).getSum())
                    .collect(Collectors.toList());

            recommendationConfigItem = new RecommendationConfigItem(percentile(0.9, doubleList), "");
        } catch (Exception e) {
            LOGGER.error("Not able to get getCPUCapacityRecommendation due to " + e.getMessage());
            recommendationConfigItem = new RecommendationConfigItem(e.getMessage());
        }
        return recommendationConfigItem;
    }

    private static RecommendationConfigItem getCPUMaxRecommendation(Map<Timestamp, StartEndTimeStampResults> filteredResultsMap) {
        RecommendationConfigItem recommendationConfigItem = null;
        try {
            Double max_cpu = filteredResultsMap.values()
                    .stream()
                    .map(e -> e.getMetrics().get(AnalyzerConstants.AggregatorType.cpuUsage).getMax() + e.getMetrics().get(AnalyzerConstants.AggregatorType.cpuThrottle).getMax())
                    .max(Double::compareTo).get();
            Double max_pods = filteredResultsMap.values()
                    .stream()
                    .map(e -> e.getMetrics().get(AnalyzerConstants.AggregatorType.cpuUsage).getSum() / e.getMetrics().get(AnalyzerConstants.AggregatorType.cpuUsage).getAvg())
                    .max(Double::compareTo).get();
            recommendationConfigItem = new RecommendationConfigItem(max_cpu * max_pods, "");
            LOGGER.debug("Max_cpu : {} , max_pods : {}", max_cpu, max_pods);
        } catch (Exception e) {
            LOGGER.error("Not able to get getCPUMaxRecommendation due to " + e.getMessage());
            recommendationConfigItem = new RecommendationConfigItem(e.getMessage());
        }
        return recommendationConfigItem;

    }

    private static RecommendationConfigItem getMemoryCapacityRecommendation(Map<Timestamp, StartEndTimeStampResults> filteredResultsMap) {
        RecommendationConfigItem recommendationConfigItem = null;
        try {
            List<Double> doubleList = filteredResultsMap.values()
                    .stream()
                    .map(e -> e.getMetrics().get(AnalyzerConstants.AggregatorType.memoryRSS).getSum())
                    .collect(Collectors.toList());

            recommendationConfigItem = new RecommendationConfigItem(percentile(0.9, doubleList), "");
        } catch (Exception e) {
            LOGGER.error("Not able to get getMemoryCapacityRecommendation due to " + e.getMessage());
            recommendationConfigItem = new RecommendationConfigItem(e.getMessage());
        }
        return recommendationConfigItem;
    }

    private static RecommendationConfigItem getMemoryMaxRecommendation(Map<Timestamp, StartEndTimeStampResults> filteredResultsMap) {
        RecommendationConfigItem recommendationConfigItem = null;
        try {
            Double max_mem = filteredResultsMap.values()
                    .stream()
                    .map(e -> e.getMetrics().get(AnalyzerConstants.AggregatorType.memoryUsage).getMax())
                    .max(Double::compareTo).get();
            Double max_pods = filteredResultsMap.values()
                    .stream()
                    .map(e -> e.getMetrics().get(AnalyzerConstants.AggregatorType.memoryUsage).getSum() / e.getMetrics().get(AnalyzerConstants.AggregatorType.memoryUsage).getAvg())
                    .max(Double::compareTo).get();
            recommendationConfigItem = new RecommendationConfigItem(max_mem * max_pods, "");
            LOGGER.debug("Max_cpu : {} , max_pods : {}", max_mem, max_pods);
        } catch (Exception e) {
            LOGGER.error("Not able to get getCPUMaxRecommendation due to " + e.getMessage());
            recommendationConfigItem = new RecommendationConfigItem(e.getMessage());
        }
        return recommendationConfigItem;

    }

    public static Timestamp addDays(Timestamp date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days);
        return new Timestamp(cal.getTime().getTime());
    }

    public static double percentile(double percentile, List<Double> items) {
        Collections.sort(items);
        return items.get((int) Math.round(percentile / 100.0 * (items.size() - 1)));
    }

}
