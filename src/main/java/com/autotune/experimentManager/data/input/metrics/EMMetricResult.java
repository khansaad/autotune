package com.autotune.experimentManager.data.input.metrics;

import com.autotune.experimentManager.data.input.interfaces.ConvertToJSON;
import com.autotune.experimentManager.exceptions.IncompatibleInputJSONException;
import com.autotune.utils.AutotuneConstants;
import org.json.JSONObject;

public class EMMetricResult implements ConvertToJSON {
    private EMMetricGenericResults emMetricGenericResults;
    private EMMetricPercentileResults emMetricPercentileResults;
    private boolean isPercentileResultsAvailable;

    public EMMetricResult() {
        emMetricGenericResults = new EMMetricGenericResults();
        emMetricPercentileResults = new EMMetricPercentileResults();
    }

    public EMMetricResult(JSONObject jsonObject) throws IncompatibleInputJSONException {
        if (!(jsonObject.has(AutotuneConstants.JSONKeys.AGGREGATION_INFO) ||
                jsonObject.has(AutotuneConstants.JSONKeys.PERCENTILE_INFO))) {
            throw new IncompatibleInputJSONException();
        }
        if (jsonObject.has(AutotuneConstants.JSONKeys.PERCENTILE_INFO)) {
            isPercentileResultsAvailable = true;
        }
        emMetricGenericResults = new EMMetricGenericResults(jsonObject.getJSONObject(AutotuneConstants.JSONKeys.AGGREGATION_INFO));
        if (isPercentileResultsAvailable) {
            emMetricPercentileResults = new EMMetricPercentileResults(jsonObject.getJSONObject(AutotuneConstants.JSONKeys.PERCENTILE_INFO));
        }
    }

    public EMMetricGenericResults getEmMetricGenericResults() {
        return emMetricGenericResults;
    }

    public void setEmMetricGenericResults(EMMetricGenericResults emMetricGenericResults) {
        this.emMetricGenericResults = emMetricGenericResults;
    }

    public EMMetricPercentileResults getEmMetricPercentileResults() {
        return emMetricPercentileResults;
    }

    public void setEmMetricPercentileResults(EMMetricPercentileResults emMetricPercentileResults) {
        this.emMetricPercentileResults = emMetricPercentileResults;
    }

    public boolean isPercentileResultsAvailable() {
        return isPercentileResultsAvailable;
    }

    public void setPercentileResultsAvailable(boolean percentileResultsAvailable) {
        isPercentileResultsAvailable = percentileResultsAvailable;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(AutotuneConstants.JSONKeys.AGGREGATION_INFO, emMetricGenericResults.toJSON());
        if (isPercentileResultsAvailable) {
            jsonObject.put(AutotuneConstants.JSONKeys.PERCENTILE_INFO, emMetricPercentileResults.toJSON());
        }
        return jsonObject;
    }
}
