/*
 * Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.data.operations;

/**
 * Enum that defines all the operations supported by the adapter,
 * IB - non-vPro devices, provisioned through EMA Agent
 * OOB - vPro devices, provisioned through CIRA
 *
 * @author Maksym.Rossiitsev/Symphony Team
 * @since 1.0.0
 * */
public enum Operation {
    // Single - we support these for sure
    OOB_SINGLE_POWER_ON("OOBOperations#PowerOn", "/api/latest/endpointOOBOperations/Single/PowerOn"),
    OOB_SINGLE_SLEEP_LIGHT("OOBOperations#SleepLight", "/api/latest/endpointOOBOperations/Single/Sleep/Light"),
    OOB_SINGLE_SLEEP_DEEP("OOBOperations#SleepDeep", "/api/latest/endpointOOBOperations/Single/Sleep/Deep"),
    OOB_SINGLE_CYCLE_OFF_SOFT("OOBOperations#CycleOffSoft", "/api/latest/endpointOOBOperations/Single/PowerCycle/OffSoft"),
    OOB_SINGLE_OFF_HARD("OOBOperations#PowerOffHard", "/api/latest/endpointOOBOperations/Single/PowerOff/Hard"),
    OOB_SINGLE_HIBERNATE("OOBOperations#Hibernate", "/api/latest/endpointOOBOperations/Single/Hibernate"),
    OOB_SINGLE_POWER_OFF_SOFT("OOBOperations#PowerOffSoft", "/api/latest/endpointOOBOperations/Single/PowerCycle/OffSoft"),
    OOB_SINGLE_CYCLE_OFF_HARD("OOBOperations#CycleOffHard", "/api/latest/endpointOOBOperations/Single/PowerCycle/OffHard"),
    OOB_SINGLE_MASTER_BUS_RESET("OOBOperations#MasterBusReset", "/api/latest/endpointOOBOperations/Single/MasterBusReset"),
    OOB_SINGLE_MASTER_BUS_RESET_GRACEFUL("OOBOperations#MasterBusResetGraceful", "/api/latest/endpointOOBOperations/Single/MasterBusReset/Graceful"),
    OOB_SINGLE_POWER_OFF_SOFT_GRACEFUL("OOBOperations#PowerOffSoftGraceful", "/api/latest/endpointOOBOperations/Single/PowerOff/SoftGraceful"),
    OOB_SINGLE_POWER_OFF_HARD_GRACEFUL("OOBOperations#PowerOffHardGraceful", "/api/latest/endpointOOBOperations/Single/PowerOff/HardGraceful"),
    OOB_SINGLE_CYCLE_OFF_SOFT_GRACEFUL("OOBOperations#CycleOffSoftGraceful", "/api/latest/endpointOOBOperations/Single/PowerCycle/OffSoftGraceful"),
    OOB_SINGLE_CYCLE_OFF_HARD_GRACEFUL("OOBOperations#CycleOffHardGraceful", "/api/latest/endpointOOBOperations/Single/PowerCycle/OffHardGraceful"),
    OOB_SINGLE_CYCLE_BOOT_TO_USBR_ISO("OOBOperations#CycleBootToUSBRIso", "/api/latest/endpointOOBOperations/Single/PowerCycle/BootToUsbrIso"),
    OOB_SINGLE_CYCLE_BOOT_TO_USBR_IMG("OOBOperations#CycleBootToUSBBRImg", "/api/latest/endpointOOBOperations/Single/PowerCycle/BootToUsbrImg"),
    OOB_SINGLE_CYCLE_BOOT_TO_BIOS("OOBOperations#CycleBootToBIOS", "/api/latest/endpointOOBOperations/Single/PowerCycle/BootToBios"),

    IB_REBOOT("IBOperations#Reboot","/api/latest/endpointIBOperations/reboot"),
    IB_SLEEP("IBOperations#Sleep", "/api/latest/endpointIBOperations/sleep"),
    IB_HIBERNATE("IBOperations#Hibernate", "/api/latest/endpointIBOperations/hibernate"),
    IB_ALERT("IBOperations#Alert", "/api/latest/endpointIBOperations/alert"),
    IB_SHUTDOWN("IBOperations#Shutdown", "/api/latest/endpointIBOperations/shutdown");

    private String propertyName;
    private String uri;

    Operation(String propertyName, String uri) {
        this.propertyName = propertyName;
        this.uri = uri;
    }

    /**
     * Retrieves {@link #propertyName}
     *
     * @return value of {@link #propertyName}
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Retrieves {@link #uri}
     *
     * @return value of {@link #uri}
     */
    public String getUri() {
        return uri;
    }

    public static Operation getByPropertyName(String propertyName) {
        for (Operation operation : Operation.values()) {
            if (operation.getPropertyName().equals(propertyName)) {
                return operation;
            }
        }
        return null;
    }
}
