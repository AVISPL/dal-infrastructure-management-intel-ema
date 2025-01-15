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
    OOB_SINGLE_POWER_ON("OOBOperations#PowerUp", "/api/latest/endpointOOBOperations/Single/PowerOn"),
    OOB_SINGLE_OFF_SOFT("OOBOperations#PowerDown", "/api/latest/endpointOOBOperations/Single/PowerOff/Soft"),
    OOB_SINGLE_CYCLE_OFF_SOFT("OOBOperations#Restart", "/api/latest/endpointOOBOperations/Single/PowerCycle/OffSoft"),
    OOB_SINGLE_CYCLE_BOOT_TO_BIOS("OOBOperations#CycleBootToBIOS", "/api/latest/endpointOOBOperations/Single/PowerCycle/BootToBios"),
    OOB_SINGLE_HIBERNATE("OOBOperations#Hibernate", "/api/latest/endpointOOBOperations/Single/Hibernate"),
    OOB_SINGLE_SLEEP_DEEP("OOBOperations#Sleep", "/api/latest/endpointOOBOperations/Single/Sleep/Deep"),

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
        for (Operation operation : values()) {
            if (operation.getPropertyName().equals(propertyName)) {
                return operation;
            }
        }
        return null;
    }
}
