/*
 * Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.data.AccessToken;
import com.avispl.symphony.dal.communicator.data.Constant;
import com.avispl.symphony.dal.communicator.data.operations.Operation;
import com.avispl.symphony.dal.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.avispl.symphony.dal.communicator.data.operations.Operation.IB_ALERT;
import static com.avispl.symphony.dal.util.ControllablePropertyFactory.createButton;
import static com.avispl.symphony.dal.util.ControllablePropertyFactory.createText;

/**
 * Intel Endpoint Management Assistant Aggregator
 * Supported features:
 * - IB/OOB Endpoints monitoring
 * - AMT Hardware details
 * - AMT Setup details
 * - EMA Server information
 * - Audit Events monitoring and filtering
 *
 * @author Maksym.Rossiitsev/Symphony Team
 * @since 1.0.0
 */
public class EMAAggregatorCommunicator extends RestCommunicator implements Aggregator, Monitorable, Controller {

    /**
     * Interceptor for RestTemplate that checks for the response headers populated for certain endpoints
     * such as metrics, to control the amount of requests left per day.
     *
     * @author Maksym.Rossiytsev
     * @since 1.0.0
     */
    class EMAHeaderInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            ClientHttpResponse response = null;
            try {
                response = execution.execute(request, body);
                if (response != null && (response.getRawStatusCode() == 403 || response.getRawStatusCode() == 401)) {
                    authenticate();
                    return execution.execute(request, body);
                }
                return response;
            } catch (Exception e) {
                logger.error("An exception occurred during request execution", e);
            }
            return response;
        }
    }

    /**
     * Process that is running constantly and triggers collecting data from EMA API endpoints, based on the given timeouts and thresholds.
     *
     * @author Maksym.Rossiytsev
     * @since 1.0.0
     */
    class EMAEndpointsDataLoader implements Runnable {
        private volatile boolean inProgress;

        public EMAEndpointsDataLoader() {
            logDebugMessage("Creating new device data loader.");
            inProgress = true;
        }

        @Override
        public void run() {
            logDebugMessage("Entering device data loader active stage.");
            mainloop:
            while (inProgress) {
                long startCycle = System.currentTimeMillis();
                try {
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted exception in data retrieval cycle.");
                    }

                    if (!inProgress) {
                        logDebugMessage("Main data collection thread is not in progress, breaking.");
                        break mainloop;
                    }

                    updateAggregatorStatus();
                    // next line will determine whether EMA monitoring was paused
                    if (devicePaused) {
                        logDebugMessage("The device communicator is paused, data collector is not active.");
                        continue mainloop;
                    }
                    try {
                        logDebugMessage("Fetching devices list.");
                        fetchEMAEndpoints();
                    } catch (Exception e) {
                        logger.error("Error occurred during device list retrieval: " + e.getMessage(), e);
                    }

                    if (!inProgress) {
                        logDebugMessage("The data collection thread is not in progress. Breaking the loop.");
                        break mainloop;
                    }

                    int aggregatedDevicesCount = aggregatedDevices.size();
                    if (aggregatedDevicesCount == 0) {
                        logDebugMessage("No devices collected in the main data collection thread so far. Continuing.");
                        continue mainloop;
                    }

                    for (AggregatedDevice aggregatedDevice : aggregatedDevices.values()) {
                        if (!inProgress) {
                            logDebugMessage("The data collection thread is not in progress. Breaking the data update loop.");
                            break;
                        }
                        if (executorService == null) {
                            logDebugMessage("Executor service reference is null. Breaking the execution.");
                            break;
                        }
                        devicesExecutionPool.add(executorService.submit(() -> {
                            String deviceId = aggregatedDevice.getDeviceId();
                            try {
                                fetchEndpointGroupDetails(aggregatedDevice);
                                fetchEndpointDetails(deviceId);
                                fetchPlatformCapabilities(deviceId);
                                fetchAMTSetup(deviceId);
                                fetchAMTHardwareInformation(deviceId);

                                operationLock.lock();
                                try {
                                    // Cleaning up the controls in case there will be none generated and we don't leave controls we don't need
                                    List<AdvancedControllableProperty> controls = aggregatedDevice.getControllableProperties();
                                    if (controls != null) {
                                        controls.clear();
                                    }
                                    generateIBControls(aggregatedDevice);
                                    generateOOBControls(aggregatedDevice);
                                } finally {
                                    operationLock.unlock();
                                }
                            } catch (Exception e) {
                                logger.error(String.format("Exception during Endpoint '%s' data processing.", aggregatedDevice.getDeviceName()), e);
                            }
                        }));
                    }
                    do {
                        try {
                            TimeUnit.MILLISECONDS.sleep(500);
                        } catch (InterruptedException e) {
                            logger.error("Interrupted exception during main loop execution", e);
                            if (!inProgress) {
                                logDebugMessage("Breaking after the main loop execution");
                                break;
                            }
                        }
                        devicesExecutionPool.removeIf(Future::isDone);
                    } while (!devicesExecutionPool.isEmpty());

                    // We don't want to fetch devices statuses too often, so by default it's currentTime + 30s
                    // otherwise - the variable is reset by the retrieveMultipleStatistics() call, which
                    // launches devices detailed statistics collection
                    nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + 30000;
                    lastMonitoringCycleDuration = (System.currentTimeMillis() - startCycle)/1000;
                    endpointGroupData.clear();
                    logDebugMessage("Finished collecting devices statistics cycle at " + new Date() + ", total duration: " + lastMonitoringCycleDuration);

                    while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        } catch (InterruptedException e) {
                            logger.warn("Interrupted exception during data collection cycle idle state.");
                        }
                    }
                } catch(Exception e) {
                    logger.error("Unexpected error occurred during main device collection cycle", e);
                }
            }
            logDebugMessage("Main device collection loop is completed, in progress marker: " + inProgress);
            // Finished collecting
        }

        /**
         * Triggers main loop to stop
         */
        public void stop() {
            logDebugMessage("Main device details collection loop is stopped!");
            inProgress = false;
        }

        /**
         * Retrieves {@link #inProgress}
         *
         * @return value of {@link #inProgress}
         */
        public boolean isInProgress() {
            return inProgress;
        }
    }
    private AccessToken accessToken;
    private List<String> auditEventActionTypeFilter;
    private List<String> auditEventResourceTypeFilter;
    private List<String> auditEventSourceFilter;
    private List<String> displayPropertyGroups;

    private int auditEventsTotal = 10;
    private int alertDuration = 5;

    private final AggregatedDeviceProcessor aggregatedDeviceProcessor;
    private final Map<String, PropertiesMapping> mapping;
    private final ReentrantLock operationLock = new ReentrantLock();

    /**
     * Interceptor for RestTemplate that is responsible for authorization token recovery
     */
    private ClientHttpRequestInterceptor emaHeaderInterceptor = new EMAHeaderInterceptor();

    /**
     * Device adapter instantiation timestamp.
     */
    private long adapterInitializationTimestamp;

    /**
     * If the {@link EMAAggregatorCommunicator#deviceMetaDataRetrievalTimeout} is set to a value that is too small -
     * devices list will be fetched too frequently. In order to avoid this - the minimal value is based on this value.
     */
    private static final long defaultMetaDataTimeout = 60 * 1000 / 2;

    /**
     * Device metadata retrieval timeout. The general devices list is retrieved once during this time period.
     */
    private long deviceMetaDataRetrievalTimeout = 60 * 1000 / 2;

    /**
     * Number of threads assigned for the data collection jobs
     * */
    private int executorServiceThreadCount = 8;

    /**
     * Aggregator inactivity timeout. If the {@link EMAAggregatorCommunicator#retrieveMultipleStatistics()}  method is not
     * called during this period of time - device is considered to be paused, thus the Cloud API
     * is not supposed to be called
     */
    private static final long retrieveStatisticsTimeOut = 180000;

    /**
     * Indicates whether a device is considered as paused.
     * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
     * collection unless the {@link EMAEndpointsDataLoader#retrieveMultipleStatistics()} method is called which will change it
     * to a correct value
     */
    private volatile boolean devicePaused = true;

    /**
     * Adapter metadata, collected from the version.properties
     */
    private Properties adapterProperties;

    /**
     * How much time last monitoring cycle took to finish
     * */
    private Long lastMonitoringCycleDuration;

    /**
     * This parameter holds timestamp of when we need to stop performing API calls
     * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
     */
    private volatile long validRetrieveStatisticsTimestamp;

    /**
     * Time period within which the device metadata (basic devices information) cannot be refreshed.
     * Ignored if device list is not yet retrieved or the cached device list is empty {@link EMAAggregatorCommunicator#aggregatedDevices}
     */
    private volatile long validDeviceMetaDataRetrievalPeriodTimestamp;

    /**
     * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
     * new devices statistics loop will be launched before the next monitoring iteration. To avoid that -
     * this variable stores a timestamp which validates it, so when the devices statistics is done collecting, variable
     * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
     * {@link #aggregatedDevices} resets it to the currentTime timestamp, which will re-activate data collection.
     */
    private volatile long nextDevicesCollectionIterationTimestamp;

    /**
     * Pool for keeping all the async operations in, to track any operations in progress and cancel them if needed
     */
    private List<Future> devicesExecutionPool = new ArrayList<>();

    /**
     * Executor that runs all the async operations, that {@link #deviceDataLoader} is posting and
     * {@link #devicesExecutionPool} is keeping track of
     */
    private ExecutorService executorService;

    /**
     * Runner service responsible for collecting data and posting processes to {@link #devicesExecutionPool}
     */
    private EMAEndpointsDataLoader deviceDataLoader;

    /**
     * Devices this aggregator is responsible for
     * Data is cached and retrieved every {@link #defaultMetaDataTimeout}
     */
    private ConcurrentHashMap<String, AggregatedDevice> aggregatedDevices = new ConcurrentHashMap<>();

    /**
     * Endpoint group data cache, cleaned up at the end of every cycle, so we can use data presence as an indicator of
     * whether we need to fetch the group data or not
     * */
    private ConcurrentHashMap<String, Map<String, String>> endpointGroupData = new ConcurrentHashMap<>();

    /**
     * Retrieves {@link #auditEventSourceFilter}
     *
     * @return value of {@link #auditEventSourceFilter}
     */
    public String getAuditEventSourceFilter() {
        return String.join(",",auditEventSourceFilter);
    }

    /**
     * Sets {@link #auditEventSourceFilter} value
     *
     * @param auditEventSourceFilter new value of {@link #auditEventSourceFilter}
     */
    public void setAuditEventSourceFilter(String auditEventSourceFilter) {
        this.auditEventSourceFilter = Arrays.stream(auditEventSourceFilter.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toList());
    }

    /**
     * Retrieves {@link #auditEventResourceTypeFilter}
     *
     * @return value of {@link #auditEventResourceTypeFilter}
     */
    public String getAuditEventResourceTypeFilter() {
        return String.join(",", auditEventResourceTypeFilter);
    }

    /**
     * Sets {@link #auditEventResourceTypeFilter} value
     *
     * @param auditEventResourceTypeFilter new value of {@link #auditEventResourceTypeFilter}
     */
    public void setAuditEventResourceTypeFilter(String auditEventResourceTypeFilter) {
        this.auditEventResourceTypeFilter = Arrays.stream(auditEventResourceTypeFilter.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toList());
    }

    /**
     * Retrieves {@link #displayPropertyGroups}
     *
     * @return value of {@link #displayPropertyGroups}
     */
    public String getDisplayPropertyGroups() {
        return String.join(",", displayPropertyGroups);
    }

    /**
     * Sets {@link #displayPropertyGroups} value
     *
     * @param displayPropertyGroups new value of {@link #displayPropertyGroups}
     */
    public void setDisplayPropertyGroups(String displayPropertyGroups) {
        this.displayPropertyGroups = Arrays.stream(displayPropertyGroups.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toList());
    }

    /**
     * Retrieves {@link #alertDuration}
     *
     * @return value of {@link #alertDuration}
     */
    public int getAlertDuration() {
        return alertDuration;
    }

    /**
     * Sets {@link #alertDuration} value
     * Min:max values are 0:300
     *
     * @param alertDuration new value of {@link #alertDuration}
     */
    public void setAlertDuration(int alertDuration) {
        this.alertDuration = alertDuration > 300 ? 300 : Math.max(alertDuration, 0);
    }

    /**
     * Retrieves {@link #auditEventActionTypeFilter}
     *
     * @return value of {@link #auditEventActionTypeFilter}
     */
    public String getAuditEventActionTypeFilter() {
        return String.join(",", auditEventActionTypeFilter);
    }

    /**
     * Sets {@link #auditEventActionTypeFilter} value
     *
     * @param auditEventTypeFilter new value of {@link #auditEventActionTypeFilter}
     */
    public void setAuditEventActionTypeFilter(String auditEventTypeFilter) {
        this.auditEventActionTypeFilter = Arrays.stream(auditEventTypeFilter.split(",")).map(String::trim).filter(StringUtils::isNotNullOrEmpty).collect(Collectors.toList());
    }

    /**
     * Retrieves {@link #deviceMetaDataRetrievalTimeout}
     *
     * @return value of {@link #deviceMetaDataRetrievalTimeout}
     */
    public long getDeviceMetaDataRetrievalTimeout() {
        return deviceMetaDataRetrievalTimeout;
    }

    /**
     * Sets {@link #deviceMetaDataRetrievalTimeout} value
     *
     * @param deviceMetaDataRetrievalTimeout new value of {@link #deviceMetaDataRetrievalTimeout}
     */
    public void setDeviceMetaDataRetrievalTimeout(long deviceMetaDataRetrievalTimeout) {
        this.deviceMetaDataRetrievalTimeout = Math.max(defaultMetaDataTimeout, deviceMetaDataRetrievalTimeout);
    }

    /**
     * Retrieves {@link #executorServiceThreadCount}
     *
     * @return value of {@link #executorServiceThreadCount}
     */
    public int getExecutorServiceThreadCount() {
        return executorServiceThreadCount;
    }

    /**
     * Sets {@link #executorServiceThreadCount} value
     *
     * @param executorServiceThreadCount new value of {@link #executorServiceThreadCount}
     */
    public void setExecutorServiceThreadCount(int executorServiceThreadCount) {
        if (executorServiceThreadCount == 0) {
            this.executorServiceThreadCount = 8;
        } else {
            this.executorServiceThreadCount = executorServiceThreadCount;
        }
    }

    /**
     * Retrieves {@link #auditEventsTotal}
     *
     * @return value of {@link #auditEventsTotal}
     */
    public int getAuditEventsTotal() {
        return auditEventsTotal;
    }

    /**
     * Sets {@link #auditEventsTotal} value
     *
     * @param auditEventsTotal new value of {@link #auditEventsTotal}
     */
    public void setAuditEventsTotal(int auditEventsTotal) {
        this.auditEventsTotal = auditEventsTotal;
    }

    public EMAAggregatorCommunicator() throws IOException {
        this.setTrustAllCertificates(true);

        mapping = new PropertiesMappingParser().loadYML("mapping/model-mapping.yml", getClass());
        aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
        adapterProperties = new Properties();
        adapterProperties.load(getClass().getResourceAsStream("/version.properties"));

        executorService = Executors.newFixedThreadPool(executorServiceThreadCount);
        executorService.submit(deviceDataLoader = new EMAEndpointsDataLoader());
    }

    @Override
    protected void internalDestroy() {
        try {
            if (deviceDataLoader != null) {
                deviceDataLoader.stop();
                deviceDataLoader = null;
            }
            devicesExecutionPool.forEach(future -> future.cancel(true));
            devicesExecutionPool.clear();
            aggregatedDevices.clear();
        } finally {
            super.internalDestroy();
        }
    }

    @Override
    protected void internalInit() throws Exception {
        adapterInitializationTimestamp = System.currentTimeMillis();
        long currentTimestamp = System.currentTimeMillis();
        validDeviceMetaDataRetrievalPeriodTimestamp = currentTimestamp;
        validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;

        super.internalInit();
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        String propertyName = controllableProperty.getProperty();
        String endpointId = controllableProperty.getDeviceId();
        String value = String.valueOf(controllableProperty.getValue());

        Operation operation = Operation.getByPropertyName(propertyName);
        if (operation != null) {
            performControlOperation(endpointId, operation, value);
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> controllablePropertyList) throws Exception {
        if (CollectionUtils.isEmpty(controllablePropertyList)) {
            throw new IllegalArgumentException("Controllable properties cannot be null or empty");
        }
        for (ControllableProperty controllableProperty : controllablePropertyList) {
            controlProperty(controllableProperty);
        }
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        updateValidRetrieveStatisticsTimestamp();

        validateAccessToken();

        Map<String, String> dynamicStatistics = new HashMap<>();
        Map<String, String> statistics = new HashMap<>();
        retrieveAuditEvents(statistics);
        fetchEMAServerInfo(statistics);

        statistics.put(Constant.Properties.ADAPTER_VERSION, adapterProperties.getProperty("aggregator.version"));
        statistics.put(Constant.Properties.ADAPTER_BUILD_DATE, adapterProperties.getProperty("aggregator.build.date"));

        long adapterUptime = System.currentTimeMillis() - adapterInitializationTimestamp;
        statistics.put(Constant.Properties.ADAPTER_UPTIME_MIN, String.valueOf(adapterUptime / (1000*60)));
        statistics.put(Constant.Properties.ADAPTER_UPTIME, normalizeUptime(adapterUptime/1000));

        if (lastMonitoringCycleDuration != null) {
            dynamicStatistics.put(Constant.Properties.LAST_MONITORING_CYCLE_DURATION, String.valueOf(lastMonitoringCycleDuration));
        }
        dynamicStatistics.put(Constant.Properties.MONITORED_DEVICES_TOTAL, String.valueOf(aggregatedDevices.size()));


        ExtendedStatistics extendedStatistics = new ExtendedStatistics();
        extendedStatistics.setStatistics(statistics);
        extendedStatistics.setDynamicStatistics(dynamicStatistics);

        return Collections.singletonList(extendedStatistics);
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
        validateAccessToken();

        updateValidRetrieveStatisticsTimestamp();
        long currentTimestamp = System.currentTimeMillis();
        nextDevicesCollectionIterationTimestamp = currentTimestamp;

        for (AggregatedDevice aggregatedDevice: aggregatedDevices.values()) {
            aggregatedDevice.setTimestamp(currentTimestamp);
        }
        return new ArrayList<>(aggregatedDevices.values());
    }

    @Override
    public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
        return retrieveMultipleStatistics()
                .stream()
                .filter(aggregatedDevice -> list.contains(aggregatedDevice.getDeviceId()))
                .collect(Collectors.toList());
    }

    @Override
    protected void authenticate() throws Exception {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        formData.add("grant_type", "password");
        formData.add("username", getLogin());
        formData.add("password", getPassword());
        accessToken = doPost(Constant.URI.API_TOKEN_URI, formData, AccessToken.class);
    }

    @Override
    protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
        if (uri.equals(Constant.URI.API_TOKEN_URI)) {
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        } else {
            if (accessToken == null) {
                authenticate();
            }
            headers.add("Authorization", String.format("%s %s", accessToken.getTokenType(), accessToken.getAccessToken()));
        }
        return super.putExtraRequestHeaders(httpMethod, uri, headers);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Additional interceptor to RestTemplate that checks the amount of requests left for metrics endpoints
     */
    @Override
    protected RestTemplate obtainRestTemplate() throws Exception {
        RestTemplate restTemplate = super.obtainRestTemplate();

        List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
        if (!interceptors.contains(emaHeaderInterceptor)) {
            interceptors.add(emaHeaderInterceptor);
        }

        return restTemplate;
    }

    /**
     * Fetch devices metadata and create aggregated devices list based on received information
     *
     * @throws Exception if any critical error occurs
     * */
    private void fetchEMAEndpoints() throws Exception {
        if (accessToken == null) {
            authenticate();
        }
        long currentTimestamp = System.currentTimeMillis();
        if (validDeviceMetaDataRetrievalPeriodTimestamp > currentTimestamp) {
            logDebugMessage(String.format("General devices metadata retrieval is in cooldown. %s seconds left",
                    (validDeviceMetaDataRetrievalPeriodTimestamp - currentTimestamp) / 1000));
            return;
        }
        validDeviceMetaDataRetrievalPeriodTimestamp = currentTimestamp + deviceMetaDataRetrievalTimeout;

        JsonNode response = doGet(Constant.URI.ENDPOINTS_LIST, JsonNode.class);
        if (response == null) {
            logDebugMessage("Unable to retrieve endpoints list.");
            return;
        }
        List<AggregatedDevice> endpoints = aggregatedDeviceProcessor.extractDevices(response);

        List<String> retrievedEndpointIds = new ArrayList<>();

        endpoints.forEach(aggregatedDevice -> {
            String deviceId = aggregatedDevice.getDeviceId();
            retrievedEndpointIds.add(deviceId);

            Map<String, String> deviceProperties = aggregatedDevice.getProperties();
            String powerState = deviceProperties.get(Constant.Properties.POWER_STATE);
            if (powerState != null) {
                deviceProperties.put(Constant.Properties.POWER_STATE, Constant.Properties.POWER_STATE_VALUES.get(powerState));
            }

            if (aggregatedDevices.containsKey(deviceId)) {
                aggregatedDevices.get(deviceId).setDeviceOnline(aggregatedDevice.getDeviceOnline());
            } else {
                aggregatedDevices.put(deviceId, aggregatedDevice);
            }
        });

        // Remove rooms that were not populated by the API
        if (retrievedEndpointIds.isEmpty()) {
            // If all the devices were not populated for any specific reason (no devices available, filtering, etc)
            aggregatedDevices.clear();
        }
        aggregatedDevices.keySet().removeIf(deviceId -> !retrievedEndpointIds.contains(deviceId));

        logDebugMessage("Endpoints list fetch complete: " + aggregatedDevices);
    }

    /**
     * Fetch endpoint group details, by endpoint group id, held by aggregated device
     * Endpoint group details are fetched once per group, within one monitoring cycle
     *
     * @param aggregatedDevice to set endpoint group details to
     * @throws Exception if any error occurs
     * */
    private void fetchEndpointGroupDetails(AggregatedDevice aggregatedDevice) throws Exception {
        if (!displayPropertyGroups.contains("EndpointGroupDetails") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("EndpointGroupDetails property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        Map<String, String> endpointProperties = aggregatedDevice.getProperties();
        if (endpointProperties == null) {
            return;
        }
        String endpointGroupId = endpointProperties.get(Constant.Properties.ENDPOINT_GROUP_ID);
        if (endpointGroupId == null) {
            return;
        }
        removeMappingKeys(endpointProperties, Constant.PropertyMappingModels.ENDPOINT_GROUP);
        Map<String, String> cachedGroupInfo = endpointGroupData.get(endpointGroupId);

        if (cachedGroupInfo == null || endpointGroupData.isEmpty()) {
            JsonNode endpointGroupInfoResponse = doGet(String.format(Constant.URI.ENDPOINT_GROUP_INFO, endpointGroupId), JsonNode.class);
            cachedGroupInfo = new HashMap<>();
            aggregatedDeviceProcessor.applyProperties(cachedGroupInfo, endpointGroupInfoResponse, Constant.PropertyMappingModels.ENDPOINT_GROUP);
            endpointProperties.putAll(cachedGroupInfo);
        }

        endpointGroupData.put(endpointGroupId, cachedGroupInfo);

    }

    /**
     * Fetch additional Endpoint information, such as hardware details, network interfaces info, etc.
     * @param endpointId id of an endpoint for which to pull the details
     * @throws Exception if any critical error occurs
     * */
    private void fetchEndpointDetails(String endpointId) throws Exception {
        if (!displayPropertyGroups.contains("EndpointDetails") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("EndpointDetails property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        AggregatedDevice endpoint = aggregatedDevices.get(endpointId);
        if (endpoint == null) {
            logDebugMessage("No endpoint found in cache with id " + endpointId);
            return;
        }
        Map<String, String> existingProperties = endpoint.getProperties();
        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_DETAILS);

            Map<String, String> endpointDetails = new HashMap<>();
            JsonNode response = doGet(String.format(Constant.URI.ENDPOINT_DETAILS, endpointId), JsonNode.class);
            if (response.has(Constant.Properties.NETWORK_INTERFACES)) {
                ArrayNode networkInterfaces = (ArrayNode) response.at(Constant.URI.NETWORK_INTERFACES);
                int interfaceId = 1;
                for (JsonNode networkInterface : networkInterfaces) {
                    Map<String, String> networkInterfaceData = new HashMap<>();
                    aggregatedDeviceProcessor.applyProperties(networkInterfaceData, networkInterface, Constant.PropertyMappingModels.NETWORK_INTERFACE);
                    for (Map.Entry<String, String> entry : networkInterfaceData.entrySet()) {
                        endpointDetails.put(String.format(Constant.Properties.NETWORK_INTERFACES_TEMPLATE, interfaceId, entry.getKey()), entry.getValue());
                    }
                    interfaceId++;
                }
            }
            aggregatedDeviceProcessor.applyProperties(endpointDetails, response, Constant.PropertyMappingModels.ENDPOINT_DETAILS);
            existingProperties.putAll(endpointDetails);
        } finally {
            operationLock.unlock();
        }
    }

    /**
     * Fetch AMT Hardware information, available for the Endpoint
     * @param endpointId id of an endpoint to pull information for
     * @throws Exception if any critical error occurs
     * */
    private void fetchAMTHardwareInformation(String endpointId) throws Exception {
        if (!displayPropertyGroups.contains("AMTHardwareInformation") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("AMTHardwareInformation property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        AggregatedDevice endpoint = aggregatedDevices.get(endpointId);
        if (endpoint == null) {
            logDebugMessage("No endpoint found in cache with id " + endpointId);
            return;
        }
        Map<String, String> existingProperties = endpoint.getProperties();

        JsonNode response = null;
        try {
            response = doGet(String.format(Constant.URI.ENDPOINT_AMT_SETUP, endpointId), JsonNode.class);
        } catch (Exception e) {
            logger.warn("Unable to retrieve AMT Hardware information for endpoint " + endpointId, e);
            return;
        }

        if (response == null) {
            logger.warn("Unable to retrieve AMT Hardware information for endpoint " + endpointId);
            return;
        }

        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_AMT_HARDWARE_INFORMATION);

            Map<String, String> amtHardwareInformation = new HashMap<>();
            aggregatedDeviceProcessor.applyProperties(amtHardwareInformation, response, Constant.PropertyMappingModels.ENDPOINT_AMT_HARDWARE_INFORMATION);
            existingProperties.putAll(amtHardwareInformation);

            // TODO: which AMT serial number is the primary one?
            String serialNumber = amtHardwareInformation.get(Constant.Properties.AMT_PLATFORM_SERIAL_NUMBER);
            if (StringUtils.isNotNullOrEmpty(serialNumber)) {
                endpoint.setSerialNumber(serialNumber);
            }
        } finally {
            operationLock.unlock();
        }

        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_AMT_MEMORY_INFORMATION);
            if (response.has(Constant.Properties.AMT_PROCESSOR_INFO)) {
                ArrayNode processorInfo = (ArrayNode) response.at(Constant.URI.AMT_PROCESSOR_INFO);
                if (!processorInfo.isEmpty()) {
                    int processorId = 1;
                    for (JsonNode processor : processorInfo) {
                        Map<String, String> processorData = new HashMap<>();
                        aggregatedDeviceProcessor.applyProperties(processorData, processor, Constant.PropertyMappingModels.ENDPOINT_AMT_PROCESSOR_INFORMATION);
                        for (Map.Entry<String, String> entry : processorData.entrySet()) {
                            existingProperties.put(String.format(Constant.Properties.AMT_PROCESSOR_INFO_TEMPLATE, processorId, entry.getKey()), entry.getValue());
                        }
                        processorId++;
                    }
                }
            }
        } finally {
            operationLock.unlock();
        }

        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_AMT_PROCESSOR_INFORMATION);
            if (response.has(Constant.Properties.AMT_MEMORY_MODULE_INFO)) {
                ArrayNode memoryInfo = (ArrayNode) response.at(Constant.URI.AMT_MEMORY_MEDIA_INFO);
                if (!memoryInfo.isEmpty()) {
                    int memoryModuleId = 1;
                    for (JsonNode memoryModule : memoryInfo) {
                        Map<String, String> memoryModuleData = new HashMap<>();
                        aggregatedDeviceProcessor.applyProperties(memoryModuleData, memoryModule, Constant.PropertyMappingModels.ENDPOINT_AMT_MEMORY_INFORMATION);
                        for (Map.Entry<String, String> entry : memoryModuleData.entrySet()) {
                            existingProperties.put(String.format(Constant.Properties.AMT_MEMORY_MODULE_INFO_TEMPLATE, memoryModuleId, entry.getKey()), entry.getValue());
                        }
                        memoryModuleId++;
                    }
                }
            }
        } finally {
            operationLock.unlock();
        }

        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_AMT_STORAGE_INFORMATION);
            if (response.has(Constant.Properties.AMT_STORAGE_MEDIA_INFO)) {
                ArrayNode storageInfo = (ArrayNode) response.at(Constant.URI.AMT_STORAGE_MEDIA_INFO);
                if (!storageInfo.isEmpty()) {
                    int storageId = 1;
                    for (JsonNode storage : storageInfo) {
                        Map<String, String> storageData = new HashMap<>();
                        aggregatedDeviceProcessor.applyProperties(storageData, storage, Constant.PropertyMappingModels.ENDPOINT_AMT_STORAGE_INFORMATION);
                        for (Map.Entry<String, String> entry : storageData.entrySet()) {
                            existingProperties.put(String.format(Constant.Properties.AMT_STORAGE_MEDIA_INFO_TEMPLATE, storageId, entry.getKey()), entry.getValue());
                        }
                        storageId++;
                    }
                }
            }
        } finally {
            operationLock.unlock();
        }
    }

    /**
     * Retrieve AMT Setup details for a given endpoint
     * @param endpointId id of an endpoint to pull AMT Setup data for
     * @throws Exception if any critical error occurs
     * */
    private void fetchAMTSetup(String endpointId) throws Exception {
        if (!displayPropertyGroups.contains("AMTSetup") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("AMTSetup property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        AggregatedDevice endpoint = aggregatedDevices.get(endpointId);
        if (endpoint == null) {
            logDebugMessage("No endpoint found in cache with id " + endpointId);
            return;
        }
        Map<String, String> existingProperties = endpoint.getProperties();
        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_AMT_SETUP);

            JsonNode response = doGet(String.format(Constant.URI.ENDPOINT_AMT_SETUP, endpointId), JsonNode.class);

            Map<String, String> endpointDetails = new HashMap<>();
            aggregatedDeviceProcessor.applyProperties(endpointDetails, response, Constant.PropertyMappingModels.ENDPOINT_AMT_SETUP);
            existingProperties.putAll(endpointDetails);
        } catch (Exception e) {
            logger.warn("Unable to fetch AMT details for endpoint " + endpointId, e);
        } finally {
            operationLock.unlock();
        }
    }

    /**
     * Retrieve general information about EMA Server
     *
     * @param existingProperties property map to keep all the ema server information properties in
     * @throws Exception if any critical error occurs
     * */
    private void fetchEMAServerInfo(Map<String, String> existingProperties) throws Exception {
        if (!displayPropertyGroups.contains("EMAServerInfo") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("EMAServerInfo property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        JsonNode response = doGet(Constant.URI.EMA_SERVER_INFO, JsonNode.class);
        if (response == null) {
            logDebugMessage("Unable to retrieve EMA Server information.");
            return;
        }
        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_PLATFORM_CAPABILITIES);

            Map<String, String> serverInfo = new HashMap<>();
            aggregatedDeviceProcessor.applyProperties(serverInfo, response, Constant.PropertyMappingModels.EMA_SERVER_INFO);
            existingProperties.putAll(serverInfo);
        } finally {
            operationLock.unlock();
        }
    }

    /**
     * Retrieve Platform capabilities for a given endpoint
     * @param endpointId id of an endpoint to pull platform capabilities data for
     * @throws Exception if any critical error occurs
     * */
    private void fetchPlatformCapabilities(String endpointId) throws Exception {
        if (!displayPropertyGroups.contains("PlatformCapabilities") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("PlatformCapabilities property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        AggregatedDevice endpoint = aggregatedDevices.get(endpointId);
        if (endpoint == null) {
            logDebugMessage("No endpoint found in cache with id " + endpointId);
            return;
        }
        Map<String, String> existingProperties = endpoint.getProperties();
        operationLock.lock();
        try {
            removeMappingKeys(existingProperties, Constant.PropertyMappingModels.ENDPOINT_PLATFORM_CAPABILITIES);

            JsonNode response = doGet(String.format(Constant.URI.ENDPOINT_PLATFORM_CAPABILITIES, endpointId), JsonNode.class);

            Map<String, String> platformCapabilities = new HashMap<>();
            aggregatedDeviceProcessor.applyProperties(platformCapabilities, response, Constant.PropertyMappingModels.ENDPOINT_PLATFORM_CAPABILITIES);
            existingProperties.putAll(platformCapabilities);
        } catch (Exception e) {
            logger.warn("Unable to retrieve platform capabilities for endpoint " + endpointId);
        } finally {
            operationLock.unlock();
        }
    }

    /**
     * Remove property entries that belong tp a particular mapping model
     *
     * @param existingProperties map reference from which properties should be removed
     * @param mappingModel name of the mapping to pull property keys from
     * */
    private void removeMappingKeys(Map<String, String> existingProperties, String mappingModel) {
        Set<String> mappingKeys = retrieveMappingKeys(mappingModel);
        // Remove entry if it ends with the mapping key, so if we add prefix, we still can manage it
        for(String mappingKey : mappingKeys) {
            existingProperties.remove(mappingKey);
        }
        // make sure management interfaces and other properties with [n] are cleaned up
        existingProperties.keySet().removeIf(key -> mappingKeys.stream().anyMatch(mappingKey -> key.endsWith("]#" + mappingKey)));
    }

    /**
     * Retrieve mapping keys for a given mapping model
     *
     * @param mappingModel for which mapping keys set should be retrieved
     * @return {@link Set} of {@link String} entries, representing a model mapping
     * */
    private Set<String> retrieveMappingKeys(String mappingModel) {
        PropertiesMapping propertiesMapping = mapping.get(mappingModel);
        if (propertiesMapping == null) {
            return Collections.emptySet();
        }
        Map<String, String> properties = propertiesMapping.getProperties();
        if (properties == null) {
            return Collections.emptySet();
        }
        return properties.keySet();
    }

    /**
     * Validate and update access token. Invalid token is an expired one, or token that is equal to null.
     *
     * @throws Exception if any critical error occurs
     * */
    private void validateAccessToken() throws Exception {
        if (accessToken == null) {
            authenticate();
            return;
        }
        Date tokenExpirationDate = accessToken.getExpires();
        if (tokenExpirationDate == null || tokenExpirationDate.before(new Date())) {
            authenticate();
        }
    }

    /**
     * Retrieve audit events available on EMA Server
     *
     * @param statistics map of properties to save audit events data to
     * @throws Exception if any critical error occurs
     * */
    private void retrieveAuditEvents(Map<String, String> statistics) throws Exception {
        if (!displayPropertyGroups.contains("AuditEvent") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("AuditEvent property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        URIBuilder uriBuilder = new URIBuilder(Constant.URI.AUDIT_EVENTS_URI);
        if (auditEventActionTypeFilter != null && !auditEventActionTypeFilter.isEmpty()) {
            uriBuilder.addParameter("action", String.join(",", auditEventActionTypeFilter));
        }
        if (auditEventResourceTypeFilter != null && !auditEventResourceTypeFilter.isEmpty()) {
            uriBuilder.addParameter("resourceType", String.join(",", auditEventResourceTypeFilter));
        }
        if (auditEventSourceFilter != null && !auditEventSourceFilter.isEmpty()) {
            uriBuilder.addParameter("source", String.join(",", auditEventSourceFilter));
        }

        ArrayNode auditEvents = doGet(uriBuilder.build().toString(), ArrayNode.class);
        int entryCounter = 1;
        for (JsonNode auditEvent : auditEvents) {
            if (entryCounter > auditEventsTotal) {
                break;
            }
            Map<String, String> eventData = new HashMap<>();
            aggregatedDeviceProcessor.applyProperties(eventData, auditEvent, Constant.PropertyMappingModels.AUDIT_EVENT);

            for(Map.Entry<String, String> entry: eventData.entrySet()) {
                statistics.put(String.format(Constant.Properties.AUDIT_EVENT_TEMPLATE, entryCounter, entry.getKey()), entry.getValue());
            }
            entryCounter++;
        }
    }

    /**
     * Generate list of in-band controls for in-band endpoint
     * @param device device reference for which in-band controls should be generated
     * */
    private void generateIBControls(AggregatedDevice device) {
        if (!displayPropertyGroups.contains("IBOperations") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("IBOperations property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        if (!displayPropertyGroups.contains("EndpointGroupDetails") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("Unable to display IBOperations controls: EndpointGroupDetails property group is not present in displayPropertyGroups configuration property.");
            return;
        }
        Map<String, String> endpointProperties = device.getProperties();
        if (endpointProperties.containsKey(Constant.Properties.CIRA_CONNECTED) && endpointProperties.get(Constant.Properties.CIRA_CONNECTED).equals(Constant.PropertyValues.TRUE)) {
            logDebugMessage(String.format("Endpoint %s is OOB endpoint. Skipping IB controls generation.", device.getDeviceId()));
            return;
        }
        List<AdvancedControllableProperty> endpointControls = device.getControllableProperties();
        if (endpointControls == null) {
            endpointControls = new ArrayList<>();
            device.setControllableProperties(endpointControls);
        }

        String allowAlert = endpointProperties.get(Constant.Properties.ALLOW_ALERT);
        String allowSleep = endpointProperties.get(Constant.Properties.ALLOW_SLEEP);
//        String allowReset = endpointProperties.get(Constant.Properties.ALLOW_RESET);
//        String allowWakeup = endpointProperties.get(Constant.Properties.ALLOW_WAKEUP);
        if (Boolean.parseBoolean(allowAlert)) {
            addControllablePropertyToList(endpointControls, endpointProperties, createText(IB_ALERT.getPropertyName(), "Enter Message"));
        }
        if (Boolean.parseBoolean(allowSleep)) {
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.IB_HIBERNATE.getPropertyName(), Constant.PropertyValues.HIBERNATE, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.IB_SLEEP.getPropertyName(), Constant.PropertyValues.SLEEP, Constant.PropertyValues.PROCESSING, 0L));
        }
        addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.IB_REBOOT.getPropertyName(), Constant.PropertyValues.REBOOT, Constant.PropertyValues.PROCESSING, 0L));
        addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.IB_SHUTDOWN.getPropertyName(), Constant.PropertyValues.SHUTDOWN, Constant.PropertyValues.PROCESSING, 0L));
    }

    /**
     * Generate list of out-of-band controls for out-of-band endpoint
     * @param device device reference for which out-of-band controls should be generated
     * */
    private void generateOOBControls(AggregatedDevice device) {
        if (!displayPropertyGroups.contains("OOBOperations") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("OOBOperations property group is not present in displayPropertyGroups configuration property. Skipping.");
            return;
        }
        if (!displayPropertyGroups.contains("EndpointGroupDetails") && !displayPropertyGroups.contains("All")) {
            logDebugMessage("Unable to display OOBOperations controls: EndpointGroupDetails property group is not present in displayPropertyGroups configuration property.");
            return;
        }
        Map<String, String> endpointProperties = device.getProperties();
        if (!endpointProperties.containsKey(Constant.Properties.CIRA_CONNECTED) || !endpointProperties.get(Constant.Properties.CIRA_CONNECTED).equals(Constant.PropertyValues.TRUE)) {
            logDebugMessage(String.format("Endpoint %s is IB endpoint. Skipping OOB controls generation.", device.getDeviceId()));
            return;
        }
        List<AdvancedControllableProperty> endpointControls = device.getControllableProperties();
        if (endpointControls == null) {
            endpointControls = new ArrayList<>();
            device.setControllableProperties(endpointControls);
        }
//        String allowAlert = endpointProperties.get(Constant.Properties.ALLOW_ALERT);
        String allowSleep = endpointProperties.get(Constant.Properties.ALLOW_SLEEP);
        String allowReset = endpointProperties.get(Constant.Properties.ALLOW_RESET);
        String allowWakeup = endpointProperties.get(Constant.Properties.ALLOW_WAKEUP);

        addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_POWER_ON.getPropertyName(), Constant.PropertyValues.POWER_ON, Constant.PropertyValues.PROCESSING, 0L));
        if (Boolean.parseBoolean(allowSleep)) {
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_SLEEP_LIGHT.getPropertyName(), Constant.PropertyValues.SLEEP, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_SLEEP_DEEP.getPropertyName(), Constant.PropertyValues.SLEEP, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_HIBERNATE.getPropertyName(), Constant.PropertyValues.HIBERNATE, Constant.PropertyValues.PROCESSING, 0L));
        }
        if (Boolean.parseBoolean(allowReset)) {
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_MASTER_BUS_RESET.getPropertyName(), Constant.PropertyValues.RESET, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_MASTER_BUS_RESET_GRACEFUL.getPropertyName(), Constant.PropertyValues.RESET, Constant.PropertyValues.PROCESSING, 0L));

        }
        if (Boolean.parseBoolean(allowWakeup)) {
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_OFF_SOFT.getPropertyName(), Constant.PropertyValues.CYCLE_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_OFF_HARD.getPropertyName(), Constant.PropertyValues.POWER_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_POWER_OFF_SOFT.getPropertyName(), Constant.PropertyValues.POWER_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_OFF_HARD.getPropertyName(), Constant.PropertyValues.CYCLE_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_POWER_OFF_SOFT_GRACEFUL.getPropertyName(), Constant.PropertyValues.POWER_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_POWER_OFF_HARD_GRACEFUL.getPropertyName(), Constant.PropertyValues.POWER_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_OFF_SOFT_GRACEFUL.getPropertyName(), Constant.PropertyValues.CYCLE_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_OFF_HARD_GRACEFUL.getPropertyName(), Constant.PropertyValues.CYCLE_OFF, Constant.PropertyValues.PROCESSING, 0L));
            addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_BOOT_TO_USBR_ISO.getPropertyName(), Constant.PropertyValues.BOOT, Constant.PropertyValues.PROCESSING, 0L));
        }
        addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_BOOT_TO_USBR_IMG.getPropertyName(), Constant.PropertyValues.BOOT, Constant.PropertyValues.PROCESSING, 0L));
        addControllablePropertyToList(endpointControls, endpointProperties, createButton(Operation.OOB_SINGLE_CYCLE_BOOT_TO_BIOS.getPropertyName(), Constant.PropertyValues.BOOT, Constant.PropertyValues.PROCESSING, 0L));
        // TODO verify these witn Intel team
    }

    /**
     * Check the list of endpoint controls and add new control, if it isn't in the list
     *
     * @param controls list of controllable properties, currently present in the device's configuration
     * @param properties map of properties, describing controls' placeholders
     * @param advancedControllableProperty property to add to the list
     * */
    private void addControllablePropertyToList(List<AdvancedControllableProperty> controls, Map<String, String> properties, AdvancedControllableProperty advancedControllableProperty) {
        boolean controlExists = controls.stream().anyMatch(control -> control.getName().equals(advancedControllableProperty.getName()));
        if (!controlExists) {
            controls.add(advancedControllableProperty);
        }
        properties.put(advancedControllableProperty.getName(), "");
    }

    /**
     * Perform control operation on an endpoint
     *
     * @param endpointId endpoint id for which control operation should be addressed
     * @param operation object that describes performed operation
     * */
    private void performControlOperation(String endpointId, Operation operation, String value) throws Exception {
        if (operation == IB_ALERT) {
            Map<String, Object> request = new HashMap<>();
            Map<String, String> endpointIds = new HashMap<>();
            endpointIds.put("EndpointId", endpointId);
            request.put("Message", value);
            request.put("Duration", 5);
            request.put("EndpointIds", Collections.singletonList(endpointIds));
            doPost(operation.getUri(), request);
        } else {
            List<Map<String, String>> request = new ArrayList<>();
            Map<String, String> entry = new HashMap<>();
            entry.put("EndpointId", endpointId);
            request.add(entry);
            doPost(operation.getUri(), request);
        }
    }

    /**
     * Update the status of the device.
     * The device is considered as paused if did not receive any retrieveMultipleStatistics()
     * calls during {@link EMAAggregatorCommunicator#validRetrieveStatisticsTimestamp}
     */
    private synchronized void updateAggregatorStatus() {
        // If the adapter is destroyed out of order, we need to make sure the device isn't paused here
        if (validRetrieveStatisticsTimestamp > 0L) {
            devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
        } else {
            devicePaused = false;
        }
    }

    /**
     * Update statistics retrieval timestamp
     * */
    private synchronized void updateValidRetrieveStatisticsTimestamp() {
        validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;
        updateAggregatorStatus();
    }

    /**
     * Logging debug message with checking if it's enabled first
     *
     * @param message to log
     * */
    private void logDebugMessage(String message) {
        if (logger.isDebugEnabled()) {
            logger.debug(message);
        }
    }

    /**
     * Uptime is received in seconds, need to normalize it and make it human readable, like
     * 1 day(s) 5 hour(s) 12 minute(s) 55 minute(s)
     * Incoming parameter is may have a decimal point, so in order to safely process this - it's rounded first.
     * We don't need to add a segment of time if it's 0.
     *
     * @param uptimeSeconds value in seconds
     * @return string value of format 'x day(s) x hour(s) x minute(s) x minute(s)'
     */
    private String normalizeUptime(long uptimeSeconds) {
        StringBuilder normalizedUptime = new StringBuilder();

        long seconds = uptimeSeconds % 60;
        long minutes = uptimeSeconds % 3600 / 60;
        long hours = uptimeSeconds % 86400 / 3600;
        long days = uptimeSeconds / 86400;

        if (days > 0) {
            normalizedUptime.append(days).append(" day(s) ");
        }
        if (hours > 0) {
            normalizedUptime.append(hours).append(" hour(s) ");
        }
        if (minutes > 0) {
            normalizedUptime.append(minutes).append(" minute(s) ");
        }
        if (seconds > 0) {
            normalizedUptime.append(seconds).append(" second(s)");
        }
        return normalizedUptime.toString().trim();
    }
}
