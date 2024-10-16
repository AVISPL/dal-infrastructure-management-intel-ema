/*
 * Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter constants, storage for URIs, property mapping model names, property names and predefined values
 *
 * @author Maksym.Rossiitsev/Symphony Team
 * @since 1.0.0
 * */
public interface Constant {
    /**
     * URI endpoints used by the adapter
     * */
    interface URI {
        String ENDPOINTS_LIST = "api/latest/endpoints";
        String ENDPOINT_DETAILS = "api/latest/endpoints/%s";
        String ENDPOINT_PLATFORM_CAPABILITIES = "api/latest/endpoints/%s/PlatformCapabilities";
        String ENDPOINT_AMT_SETUP = "api/latest/amtSetups/endpoints/%s";
        String EMA_SERVER_INFO = "api/emaServerInfo";
        String AUDIT_EVENTS_URI = "api/latest/auditEvents";
        String API_TOKEN_URI = "api/token";
        String ENDPOINT_AMT_HARDWARE = "api/latest/endpoints/%s/HardwareInfoFromAmt";
        String ENDPOINT_GROUP_INFO = "/api/latest/endpointGroups/%s";
        String AMT_STORAGE_MEDIA_INFO = "/AmtStorageMediaInfo";
        String AMT_MEMORY_MEDIA_INFO = "/AmtMemoryModuleInfo";
        String AMT_PROCESSOR_INFO = "/AmtProcessorInfo";
        String NETWORK_INTERFACES = "/NetworkInterfaces";
    }
    /**
     * Property mapping model names, defined in yml
     * */
    interface PropertyMappingModels {
        String ENDPOINT_DETAILS = "EndpointDetails";
        String AUDIT_EVENT = "AuditEvent";
        String EMA_SERVER_INFO = "EMAServerInfo";
        String ENDPOINT_PLATFORM_CAPABILITIES = "PlatformCapabilities";
        String ENDPOINT_AMT_SETUP = "AMTSetup";
        String NETWORK_INTERFACE = "NetworkInterface";
        String ENDPOINT_GROUP = "EndpointGroup";
        String ENDPOINT_AMT_HARDWARE_INFORMATION = "AMTHardwareInformation";
        String ENDPOINT_AMT_PROCESSOR_INFORMATION = "AMTProcessorInformation";
        String ENDPOINT_AMT_MEMORY_INFORMATION = "AMTMemoryModuleInformation";
        String ENDPOINT_AMT_STORAGE_INFORMATION = "AmtStorageMediaInformation";
    }

    /**
     *
     * */
    class PropertiesHandler {
        /**
         * Power states map to resolve endpoint states to real values
         * 0 -> Power On
         * 1 -> Power Off
         * 2 -> Sleep
         * 3 -> Hibernating
         * 4 -> Power Cycle
         * 5 -> Modern Standby
         * 999 -> Unknown
         * */
        private static Map<String, String> powerStates = new HashMap<>();
        static {
            powerStates.put("0", "Power On");
            powerStates.put("1", "Power Off");
            powerStates.put("2", "Sleep");
            powerStates.put("3", "Hibernating");
            powerStates.put("4", "Power Cycle");
            powerStates.put("5", "Modern Standby");
            powerStates.put("999", "Unknown");
        }
        public static Map<String, String> fetchPowerStates() {
            return powerStates;
        }
    }
    /**
     * Adapter extended properties names
     * */
    interface Properties {
        String ADAPTER_UPTIME = "AdapterUptime";
        String ADAPTER_UPTIME_MIN = "AdapterUptime(min)";
        String ADAPTER_BUILD_DATE = "AdapterBuildDate";
        String ADAPTER_VERSION = "AdapterVersion";
        String MONITORED_DEVICES_TOTAL = "MonitoredDevicesTotal";
        String LAST_MONITORING_CYCLE_DURATION = "LastMonitoringCycleDuration(s)";
        String NETWORK_INTERFACES = "NetworkInterfaces";
        String NETWORK_INTERFACES_TEMPLATE = "NetworkInterface[%s]#%s";
        String AMT_PLATFORM_SERIAL_NUMBER = "AmtPlatformInfo#SerialNumber";
        String AMT_PROCESSOR_INFO = "AmtProcessorInfo";
        String AMT_PROCESSOR_INFO_TEMPLATE = "AMTProcessor[%s]#%s";
        String AMT_MEMORY_MODULE_INFO = "AmtMemoryModuleInfo";
        String AMT_MEMORY_MODULE_INFO_TEMPLATE = "AMTMemoryModule[%s]#%s";
        String AMT_STORAGE_MEDIA_INFO = "AmtStorageMediaInfo";
        String AMT_STORAGE_MEDIA_INFO_TEMPLATE = "AMTStorage[%s]#%s";
        String ENDPOINT_GROUP_ID = "EndpointGroupId";
        String AUDIT_EVENT_TEMPLATE = "AuditEvent[%s]#%s";
        String CIRA_CONNECTED = "IsCiraConnected";
        String ALLOW_ALERT = "EndpointGroup#AllowAlert";
        String ALLOW_SLEEP = "EndpointGroup#AllowSleep";
        String ALLOW_RESET = "EndpointGroup#AllowReset";
        String ALLOW_WAKEUP = "EndpointGroup#AllowWakeup";
        String ENDPOINT_DETAILS_POWER_STATE = "EndpointDetails#PowerState";

        Map<String, String> POWER_STATE_VALUES = PropertiesHandler.fetchPowerStates();
    }
    /**
     * Predefined extended property values
     * */
    interface PropertyValues {
        String PROCESSING = "Processing";
        String REBOOT = "Reboot";
        String SLEEP = "Sleep";
        String HIBERNATE = "Hibernate";
        String SHUTDOWN = "Shutdown";
        String POWER_OFF = "PowerOff";
        String CYCLE_OFF = "CycleOff";
        String POWER_ON = "PowerOn";
        String RESET = "Reset";
        String BOOT = "Boot";
        String TRUE = "true";
    }
}
