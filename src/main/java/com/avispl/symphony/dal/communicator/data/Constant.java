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
        String ENDPOINT_AMT_STORAGE_INFORMATION = "AMTStorageMediaInformation";
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

        private static Map<String, String> brands = new HashMap<>();

        private static Map<String, String> states = new HashMap<>();

        private static Map<String, String> amtControlModes = new HashMap<>();

        private static Map<String, String> amtProvisioningStates = new HashMap<>();

        private static Map<String, String> mexbPasswordStates = new HashMap<>();
        static {
            powerStates.put("0", "Power On");
            powerStates.put("1", "Power Off");
            powerStates.put("2", "Sleep");
            powerStates.put("3", "Hibernating");
            powerStates.put("4", "Power Cycle");
            powerStates.put("5", "Modern Standby");
            powerStates.put("999", "Unknown");

            brands.put("0", "Unknown");
            brands.put("1", "Intel Based");
            brands.put("2", "Intel® vPro Enterprise");
            brands.put("3", "Intel® vPro Essentials");

            states.put("0", "Unused Record");
            states.put("1", "Pending Activation");
            states.put("2", "Pending Configuration");
            states.put("3", "Pending Credential Push");
            states.put("9", "Invalid Record");
            states.put("10", "Provisioning Completed");
            states.put("11", "Pending Deactivate");
            states.put("12", "Pending Deactivate Credential Push");
            states.put("13", "Deactivate Completed");
            states.put("14", "Deactivate Error");
            states.put("15", "Retry Activation on Reconnect");
            states.put("16", "Retry Configuration on Reconnect");
            states.put("17", "Retry Credential Push on Reconnect");
            states.put("18", "Retry Deactivate on Reconnect");
            states.put("19", "Retry Deactivate Credential Push on Reconnect");
            states.put("999", "Unknown State");

            amtControlModes.put("1", "Client Control Mode");
            amtControlModes.put("2", "Admin Control Mode");
            amtControlModes.put("3", "Reserved");

            amtProvisioningStates.put("0", "Not provisioned");
            amtProvisioningStates.put("1", "Provisioning in progress");
            amtProvisioningStates.put("2", "Provisioned");

            mexbPasswordStates.put("101", "Not configured to randomize MEBX password");
            mexbPasswordStates.put("102", "Random MEBX password set pending");
            mexbPasswordStates.put("103", "Random MEBX password set failed");
            mexbPasswordStates.put("104", "Random MEBX password set succeeded");
        }
        public static Map<String, String> fetchPowerStates() {
            return powerStates;
        }

        public static Map<String, String> fetchBrands() {
            return brands;
        }

        public static Map<String, String> fetchStates() {
            return states;
        }

        public static Map<String, String> fetchAMTProvisioningStates() {
            return amtProvisioningStates;
        }

        public static Map<String, String> fetchAMTControlModes() {
            return amtControlModes;
        }

        public static Map<String, String> fetchMexbPasswordStates() {
            return mexbPasswordStates;
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
        String AMT_PLATFORM_SERIAL_NUMBER = "AMTPlatformInfo#SerialNumber";
        String AMT_PROCESSOR_INFO = "AMTProcessorInfo";
        String AMT_PROCESSOR_INFO_TEMPLATE = "AMTProcessor[%s]#%s";
        String AMT_MEMORY_MODULE_INFO = "AMTMemoryModuleInfo";
        String AMT_MEMORY_MODULE_INFO_TEMPLATE = "AMTMemoryModule[%s]#%s";
        String AMT_STORAGE_MEDIA_INFO = "AMTStorageMediaInfo";
        String AMT_STORAGE_MEDIA_INFO_TEMPLATE = "AMTStorage[%s]#%s";
        String ENDPOINT_GROUP_ID = "EndpointGroupID";
        String AUDIT_EVENT_TEMPLATE = "AuditEvent[%s]#%s";
        String CIRA_CONNECTED = "IsCIRAConnected";
        String AGENT_VERSION = "EndpointDetails#AgentVersion";
        String ALLOW_ALERT = "EndpointGroup#AllowAlert";
        String ALLOW_SLEEP = "EndpointGroup#AllowSleep";
        String ALLOW_RESET = "EndpointGroup#AllowReset";
        String ALLOW_WAKEUP = "EndpointGroup#AllowWakeup";
        String ENDPOINT_DETAILS_POWER_STATE = "EndpointDetails#PowerState";
        String ENDPOINT_DETAILS_BRAND = "EndpointDetails#Brand";
        String AMT_INFO_STATE = "AMTInfo#State";
        String AMT_PROVISIONING_STATE = "ManagementEngine#AMTProvisioningState";
        String AMT_CONTROL_MODE = "ManagementEngine#AMTControlMode";
        String MEXB_PASSWORD_STATE = "AMTProfile#MexbPasswordState";
        String IB_OPERATIONS_GROUP = "IBOperations";
        String OOB_OPERATIONS_GROUP = "OOBOperations";
        String PRIMARY_RD_URL = "RemoteControlUrlPrimary";
        String SECONDARY_RD_URL = "RemoteControlUrlSecondary";

        Map<String, String> POWER_STATE_VALUES = PropertiesHandler.fetchPowerStates();
        Map<String, String> BRAND_VALUES = PropertiesHandler.fetchBrands();
        Map<String, String> STATE_VALUES = PropertiesHandler.fetchStates();
        Map<String, String> AMT_PROVISIONING_STATES = PropertiesHandler.fetchAMTProvisioningStates();
        Map<String, String> AMT_CONTROL_MODES = PropertiesHandler.fetchAMTControlModes();
        Map<String, String> MEXB_PASSWORD_STATES = PropertiesHandler.fetchMexbPasswordStates();
    }
    /**
     * Predefined extended property values
     * */
    interface PropertyValues {
        String PROCESS = "Process";
        String PROCESSING = "Processing";
        String REBOOT = "Reboot";
        String SLEEP = "Sleep";
        String HIBERNATE = "Hibernate";
        String SHUTDOWN = "Shutdown";
        String POWER_DOWN = "PowerDown";
        String POWER_UP = "PowerUp";
        String RESTART = "Restart";
        String CYCLE_OFF = "CycleOff";
        String TRUE = "true";
    }
}
