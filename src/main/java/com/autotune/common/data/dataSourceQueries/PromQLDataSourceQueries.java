package com.autotune.common.data.dataSourceQueries;

/**
 * This class provides static constants for PromQL queries related to a Kubernetes cluster.
 * It utilizes the queries defined in the DataSourceQueries class.
 */
public class PromQLDataSourceQueries {
    public static final String NAMESPACE_QUERY = DataSourceQueries.PromQLQuery.NAMESPACE_QUERY.getQuery();
    public static final String WORKLOAD_QUERY = DataSourceQueries.PromQLQuery.WORKLOAD_INFO_QUERY.getQuery();
    public static final String CONTAINER_QUERY = DataSourceQueries.PromQLQuery.CONTAINER_INFO_QUERY.getQuery();
    public static final String CPU_USAGE = DataSourceQueries.PromQLQuery.CPU_USAGE.getQuery();
    public static final String CPU_THROTTLE = DataSourceQueries.PromQLQuery.CPU_THROTTLE.getQuery();
    public static final String CPU_LIMIT = DataSourceQueries.PromQLQuery.CPU_LIMIT.getQuery();
    public static final String CPU_REQUEST = DataSourceQueries.PromQLQuery.CPU_REQUEST.getQuery();
    public static final String MEMORY_USAGE = DataSourceQueries.PromQLQuery.MEMORY_USAGE.getQuery();
    public static final String MEMORY_RSS = DataSourceQueries.PromQLQuery.MEMORY_RSS.getQuery();
    public static final String MEMORY_LIMIT = DataSourceQueries.PromQLQuery.MEMORY_LIMIT.getQuery();
    public static final String MEMORY_REQUEST = DataSourceQueries.PromQLQuery.MEMORY_REQUEST.getQuery();
    public static final String NAMESPACE_CPU_USAGE = DataSourceQueries.PromQLQuery.NAMESPACE_CPU_USAGE.getQuery();
    public static final String NAMESPACE_CPU_THROTTLE = DataSourceQueries.PromQLQuery.NAMESPACE_CPU_THROTTLE.getQuery();
    public static final String NAMESPACE_CPU_LIMIT = DataSourceQueries.PromQLQuery.NAMESPACE_CPU_LIMIT.getQuery();
    public static final String NAMESPACE_CPU_REQUEST = DataSourceQueries.PromQLQuery.NAMESPACE_CPU_REQUEST.getQuery();
    public static final String NAMESPACE_MEMORY_USAGE = DataSourceQueries.PromQLQuery.NAMESPACE_MEMORY_USAGE.getQuery();
    public static final String NAMESPACE_MEMORY_RSS = DataSourceQueries.PromQLQuery.NAMESPACE_MEMORY_RSS.getQuery();
    public static final String NAMESPACE_MEMORY_LIMIT = DataSourceQueries.PromQLQuery.NAMESPACE_MEMORY_LIMIT.getQuery();
    public static final String NAMESPACE_MEMORY_REQUEST = DataSourceQueries.PromQLQuery.NAMESPACE_MEMORY_REQUEST.getQuery();
    public static final String NAMESPACE_MAX_DATE = DataSourceQueries.PromQLQuery.NAMESPACE_MAX_DATE.getQuery();
    public static final String MAX_DATE = DataSourceQueries.PromQLQuery.MAX_DATE.getQuery();
    public static final String NAMESPACE_TOTAL_PODS = DataSourceQueries.PromQLQuery.NAMESPACE_TOTAL_PODS.getQuery();
    public static final String NAMESPACE_TOTAL_RUNNING_PODS = DataSourceQueries.PromQLQuery.NAMESPACE_TOTAL_RUNNING_PODS.getQuery();
    public static final String GPU_CORE_USAGE = DataSourceQueries.PromQLQuery.GPU_CORE_USAGE.getQuery();
    public static final String GPU_MEMORY_USAGE = DataSourceQueries.PromQLQuery.GPU_MEMORY_USAGE.getQuery();
}
