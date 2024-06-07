/*******************************************************************************
 * Copyright (c) 2023 Red Hat, IBM Corporation and others.
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

import com.autotune.analyzer.serviceObjects.DSMetadataAPIObject;
import com.autotune.analyzer.utils.AnalyzerConstants;
import com.autotune.analyzer.utils.AnalyzerErrorConstants;
import com.autotune.analyzer.utils.GsonUTCDateAdapter;
import com.autotune.common.data.ValidationOutputData;
import com.autotune.common.data.dataSourceMetadata.DataSourceMetadataInfo;
import com.autotune.common.datasource.DataSourceInfo;
import com.autotune.common.datasource.DataSourceManager;
import com.autotune.database.service.ExperimentDBService;
import com.autotune.utils.KruizeConstants;
import com.autotune.utils.KruizeSupportedTypes;
import com.autotune.utils.MetricsConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.CHARACTER_ENCODING;
import static com.autotune.analyzer.utils.AnalyzerConstants.ServiceConstants.JSON_CONTENT_TYPE;

public class DSMetadataService extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(DSMetadataService.class);

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String statusValue = "failure";
        Timer.Sample timerImportDSMetadata = Timer.start(MetricsConfig.meterRegistry());
        //Key = dataSourceName
        HashMap<String, DataSourceMetadataInfo> dataSourceMetadataMap = new HashMap<>();
        String inputData = "";

        try {
            // Set the character encoding of the request to UTF-8
            request.setCharacterEncoding(CHARACTER_ENCODING);

            inputData = request.getReader().lines().collect(Collectors.joining());

            if (null == inputData || inputData.isEmpty()) {
                throw new Exception("Request input data cannot be null or empty");
            }

            DSMetadataAPIObject metadataAPIObject = new Gson().fromJson(inputData, DSMetadataAPIObject.class);

            ValidationOutputData validationOutputData = validateMandatoryFields(metadataAPIObject);
            if (validationOutputData.isSuccess()) {

                String dataSourceName = metadataAPIObject.getDataSourceName();

                DataSourceInfo datasource = new ExperimentDBService().loadDataSourceFromDBByName(dataSourceName);
                if (null != datasource) {
                    new DataSourceManager().importMetadataFromDataSource(datasource);
                    DataSourceMetadataInfo dataSourceMetadata = new ExperimentDBService().loadMetadataFromDBByName(dataSourceName, "false");
                    dataSourceMetadataMap.put(dataSourceName, dataSourceMetadata);
                }

                if (dataSourceMetadataMap.isEmpty() || !dataSourceMetadataMap.containsKey(dataSourceName)) {
                    sendErrorResponse(
                            inputData,
                            response,
                            new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_DATASOURCE_NAME_METADATA_EXCPTN),
                            HttpServletResponse.SC_BAD_REQUEST,
                            String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.DATASOURCE_METADATA_IMPORT_ERROR_MSG, dataSourceName)
                    );
                } else {
                    sendSuccessResponse(response, dataSourceMetadataMap.get(dataSourceName));
                }
            } else {
                sendErrorResponse(
                        response,
                        new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.MISSING_QUERY_PARAM_EXCPTN),
                        validationOutputData.getErrorCode(),
                        validationOutputData.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Unknown exception caught: " + e.getMessage());
            sendErrorResponse(inputData, response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: " + e.getMessage());
        } finally {
            if (null != timerImportDSMetadata) {
                MetricsConfig.timerImportDSMetadata = MetricsConfig.timerBImportDSMetadata.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerImportDSMetadata.stop(MetricsConfig.timerImportDSMetadata);
            }
        }

    }

    private List<String> mandatoryFields = new ArrayList<>(Arrays.asList(
            AnalyzerConstants.VERSION,
            AnalyzerConstants.DATASOURCE_NAME
    ));

    public ValidationOutputData validateMandatoryFields(DSMetadataAPIObject metadataAPIObject) {
        List<String> missingMandatoryFields = new ArrayList<>();
        ValidationOutputData validationOutputData = new ValidationOutputData(false, null, null);

        String errorMsg = "";
        mandatoryFields.forEach(
                mField -> {
                    String methodName = "get" + mField.substring(0, 1).toUpperCase() + mField.substring(1);
                    try {
                        Method getNameMethod = metadataAPIObject.getClass().getMethod(methodName);
                        if (getNameMethod.invoke(metadataAPIObject) == null) {
                            missingMandatoryFields.add(mField);
                        }
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        LOGGER.error("Method name for {} does not exist and the error is {}", mField, e.getMessage());
                    }
                }
        );

        if(!missingMandatoryFields.isEmpty()) {
            errorMsg = errorMsg.concat(String.format("Mandatory parameters missing %s ", missingMandatoryFields));
            validationOutputData.setErrorCode(HttpServletResponse.SC_BAD_REQUEST);
            validationOutputData.setSuccess(false);
            validationOutputData.setMessage(errorMsg);
        } else {
            validationOutputData.setSuccess(true);
        }

        return validationOutputData;
    }

    private void sendSuccessResponse(HttpServletResponse response, DataSourceMetadataInfo dataSourceMetadata) throws IOException {
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(CHARACTER_ENCODING);
        response.setStatus(HttpServletResponse.SC_CREATED);

        String gsonStr = "";
        if (null != dataSourceMetadata) {
            Gson gsonObj = new GsonBuilder()
                    .disableHtmlEscaping()
                    .setPrettyPrinting()
                    .enableComplexMapKeySerialization()
                    .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                    .create();
            gsonStr = gsonObj.toJson(dataSourceMetadata);
        }
        response.getWriter().println(gsonStr);
        response.getWriter().close();
    }

    public void sendErrorResponse(String inputRequestPayload, HttpServletResponse response, Exception e, int httpStatusCode, String errorMsg) throws
            IOException {
        if (null != e) {
            LOGGER.error(e.toString());
            e.printStackTrace();
            if (null == errorMsg) errorMsg = e.getMessage();
        }
        // check for the input request data to debug issues, if any
        LOGGER.debug(inputRequestPayload);
        response.sendError(httpStatusCode, errorMsg);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
        String statusValue = "failure";
        Timer.Sample timerListDSMetadata = Timer.start(MetricsConfig.meterRegistry());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding(CHARACTER_ENCODING);
        String gsonStr;

        String dataSourceName = request.getParameter(AnalyzerConstants.ServiceConstants.DATASOURCE);
        String clusterName = request.getParameter(AnalyzerConstants.ServiceConstants.CLUSTER_NAME);
        String namespace = request.getParameter(AnalyzerConstants.ServiceConstants.NAMESPACE);
        String verbose = request.getParameter(AnalyzerConstants.ServiceConstants.VERBOSE);
        String internalVerbose = "false";
        //Key = dataSource name
        HashMap<String, DataSourceMetadataInfo> dataSourceMetadataMap = new HashMap<>();
        boolean error = false;
        // validate Query params
        Set<String> invalidParams = new HashSet<>();
        for (String param : request.getParameterMap().keySet()) {
            if (!KruizeSupportedTypes.DSMETADATA_QUERY_PARAMS_SUPPORTED.contains(param)) {
                invalidParams.add(param);
            }
        }

        try {
            if (invalidParams.isEmpty()){
                if (null != verbose) {
                    internalVerbose = verbose;
                }

                if (isValidBooleanValue(internalVerbose)) {
                    try {
                        if (null == dataSourceName || dataSourceName.isEmpty()) {
                            error = true;
                            sendErrorResponse(
                                    response,
                                    new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_DATASOURCE_NAME_METADATA_EXCPTN),
                                    HttpServletResponse.SC_BAD_REQUEST,
                                    String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.DATASOURCE_NAME_MANDATORY)
                            );
                        } else {
                            try {
                                DataSourceMetadataInfo dataSourceMetadata = null;
                                if (null == clusterName) {
                                    dataSourceMetadata = new ExperimentDBService().loadMetadataFromDBByName(dataSourceName, internalVerbose);
                                } else if (null != clusterName && null == namespace) {
                                    dataSourceMetadata = new ExperimentDBService().loadMetadataFromDBByClusterName(dataSourceName, clusterName, internalVerbose);
                                } else if (null != clusterName && null != namespace) {
                                    internalVerbose = "true";
                                    dataSourceMetadata = new ExperimentDBService().loadMetadataFromDBByNamespace(dataSourceName, clusterName, namespace);
                                }

                                if (null == dataSourceMetadata) {
                                    error = true;
                                    String errorMessage;
                                    Exception exception;

                                    if (null == clusterName) {
                                        exception = new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_DATASOURCE_NAME_METADATA_EXCPTN);
                                        errorMessage = String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_DATASOURCE_NAME_METADATA_MSG, dataSourceName);
                                    } else if (null == namespace) {
                                        exception = new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_DATASOURCE_NAME_CLUSTER_NAME_METADATA_EXCPTN);
                                        errorMessage = String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_DATASOURCE_NAME_CLUSTER_NAME_METADATA_MSG, dataSourceName, clusterName);
                                    } else {
                                        exception = new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.MISSING_DATASOURCE_METADATA_EXCPTN);
                                        errorMessage = String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.MISSING_DATASOURCE_METADATA_MSG, dataSourceName, clusterName, namespace);
                                    }

                                    sendErrorResponse(
                                            response,
                                            exception,
                                            HttpServletResponse.SC_BAD_REQUEST,
                                            errorMessage
                                    );
                                }

                                dataSourceMetadataMap.put(dataSourceName, dataSourceMetadata);
                            } catch (Exception e) {
                                LOGGER.error("Loading saved Datasource metadata {} failed: {} ", dataSourceName, e.getMessage());
                            }
                        }

                        if (!error) {
                            // create Gson Object
                            Gson gsonObj = createGsonObject();
                            gsonStr = gsonObj.toJson(dataSourceMetadataMap.get(dataSourceName));
                            response.getWriter().println(gsonStr);
                            response.getWriter().close();
                            statusValue = "success";
                        }
                    } catch (Exception e) {
                        LOGGER.error("Exception: " + e.getMessage());
                        e.printStackTrace();
                        sendErrorResponse(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    }
                } else {
                    sendErrorResponse(
                            response,
                            new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_QUERY_PARAM_EXCPTN),
                            HttpServletResponse.SC_BAD_REQUEST,
                            String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_QUERY_PARAM_VALUE, AnalyzerConstants.ServiceConstants.VERBOSE)
                    );
                }
            } else {
                sendErrorResponse(
                        response,
                        new Exception(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_QUERY_PARAM_EXCPTN),
                        HttpServletResponse.SC_BAD_REQUEST,
                        String.format(AnalyzerErrorConstants.APIErrors.DSMetadataAPI.INVALID_QUERY_PARAM, invalidParams)
                );
            }
        } catch (Exception e) {
            LOGGER.error("Exception: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse(response, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            if (null != timerListDSMetadata) {
                MetricsConfig.timerListDSMetadata = MetricsConfig.timerBListDSMetadata.tag("status", statusValue).register(MetricsConfig.meterRegistry());
                timerListDSMetadata.stop(MetricsConfig.timerListDSMetadata);
            }
        }
    }

    public void sendErrorResponse(HttpServletResponse response, Exception e, int httpStatusCode, String errorMsg) throws
            IOException {
        if (null != e) {
            LOGGER.error(e.toString());
            e.printStackTrace();
            if (null == errorMsg) errorMsg = e.getMessage();
        }
        response.sendError(httpStatusCode, errorMsg);
    }
    private Gson createGsonObject() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .setPrettyPrinting()
                .enableComplexMapKeySerialization()
                .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
                .create();
    }
    private boolean isValidBooleanValue(String value) {
        return value != null && (value.equals("true") || value.equals("false"));
    }
}
