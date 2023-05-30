# Custom Prometheus Queries for Kruize

These are the custom Prometheus queries that you can use while running Kruize. These queries provide valuable insights into the performance of Kruize APIs and KruizeDB methods.

## KruizeAPI Metrics

The following are the available Kruize APIs that you can monitor:

- `createExperiment` (POST): API for creating a new experiment.
- `listRecommendations` (GET): API for listing recommendations.
- `listExperiments` (GET): API for listing experiments.
- `updateResults` (POST): API for updating experiment results.

## Time taken for KruizeAPI metrics

To monitor the performance of these APIs, you can use the following metrics:

- `kruizeAPI_seconds_count`: This metric provides the count of invocations for a specific API. It measures how many times the API has been called.
- `kruizeAPI_seconds_sum`: This metric provides the sum of the time taken by a specific API. It measures the total time consumed by the API across all invocations.
- `kruizeAPI_seconds_max`: This metric provides the maximum time taken by a specific API. It measures the highest execution time observed for the API.

Here are some sample metrics for the mentioned APIs which can run in Prometheus:

- `kruizeAPI_seconds_count{api="createExperiment", application="Kruize", method="POST"}`: Returns the count of invocations for the `createExperiment` API.
- `kruizeAPI_seconds_sum{api="createExperiment", application="Kruize", method="POST"}`: Returns the sum of the time taken by the `createExperiment` API.
- `kruizeAPI_seconds_max{api="createExperiment", application="Kruize", method="POST"}`: Returns the maximum time taken by the `createExperiment` API.

By changing the value of the `api` and `method` label, you can gather metrics for other Kruize APIs such as `listRecommendations`, `listExperiments`, and `updateResults`.

Here is a sample command to collect the metric through `curl`
- `curl --silent -G -kH "Authorization: Bearer ${TOKEN}" --data-urlencode 'query=kruizeAPI_seconds_sum{api="listRecommendations", application="Kruize", method="GET"}' ${PROMETHEUS_URL} | jq` : 
Returns the sum of the time taken by `listRecommendations` API.
  
Sample Output:
```
{
"status": "success",
"data": {
"resultType": "vector",
"result": [
{
"metric": {
"__name__": "kruizeAPI_seconds_sum",
"api": "listRecommendations",
"application": "Kruize",
"container": "kruize",
"endpoint": "kruize-port",
"instance": "10.129.9.99:8080",
"job": "kruize",
"method": "GET",
"namespace": "openshift-tuning",
"pod": "kruize-7c97865bbf-tw8zb",
"prometheus": "openshift-user-workload-monitoring/user-workload",
"service": "kruize"
},
"value": [
1685015801.127,
"7.626040199"
]
}]}}
```

## KruizeDB Metrics

The following are the available Kruize DB methods that you can monitor:

- `addRecommendationToDB`: Method for adding a recommendation to the database.
- `addResultsToDB`: Method for adding experiment results to the database.
- `loadAllRecommendations`: Method for loading all recommendations from the database.
- `loadAllExperiments`: Method for loading all experiments from the database.
- `addExperimentToDB`: Method for adding an experiment to the database.
- `loadResultsByExperimentName`: Method for loading experiment results by experiment name.
- `loadExperimentByName`: Method for loading an experiment by name.
- `loadAllResults`: Method for loading all experiment results from the database.
- `loadRecommendationsByExperimentName`: Method for loading recommendations by experiment name.

## Time taken for KruizeDB metrics

To monitor the performance of these methods, you can use the following metrics:

- `kruizeDB_seconds_count`: This metric provides the count of calls made to the specific DB method. It measures how many times the DB method has been called.
- `kruizeDB_seconds_sum`: This metric provides the sum of the time taken by a specific DB method. It measures the total time consumed by the DB method across all invocations.
- `kruizeDB_seconds_max`: This metric provides the maximum time taken by a specific DB method. It measures the highest execution time observed for the DB method.

Here are some sample metrics for the mentioned DB methods which can run in Prometheus:

- `kruizeDB_seconds_count{application="Kruize", method="loadAllExperiments"}`: Number of times the `loadAllExperiments` method was called.
- `kruizeDB_seconds_sum{application="Kruize", method="loadAllExperiments"}`: Total time taken by the `loadAllExperiments` method.
- `kruizeDB_seconds_max{application="Kruize", method="loadAllExperiments"}`: Maximum time taken by the `loadAllExperiments` method.

By changing the value of the `method` label, you can gather metrics for other KruizeDB metrics.

Here is a sample command to collect the metric through `curl`
- `curl --silent -G -kH "Authorization: Bearer ${TOKEN}" --data-urlencode 'query=kruizeDB_seconds_sum{method="loadRecommendationsByExperimentName"}' ${PROMETHEUS_URL} | jq` :
  Returns the sum of the time taken by `loadRecommendationsByExperimentName` method.

Sample Output:
```
{
"status": "success",
"data": {
"resultType": "vector",
"result": [
{
"metric": {
"__name__": "kruizeDB_seconds_sum",
"application": "Kruize",
"container": "kruize",
"endpoint": "kruize-port",
"instance": "10.129.9.99:8080",
"job": "kruize",
"method": "loadRecommendationsByExperimentName",
"namespace": "openshift-tuning",
"pod": "kruize-7c97865bbf-tw8zb",
"prometheus": "openshift-user-workload-monitoring/user-workload",
"service": "kruize"
},
"value": [
1685016497.066,
"1.863846208"
]
}]}}
```

> Note: Ensure that you have Prometheus set up and enabled ServiceMonitor to collect these metrics.
