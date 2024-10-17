/*
 * Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.List;

public class AggregatorCommunicatorTest {

    EMAAggregatorCommunicator communicator;

    @BeforeEach
    public void setUp() throws Exception {
        communicator = new EMAAggregatorCommunicator();
        communicator.setHost("10.151.16.89");
        communicator.setProtocol("https");
        communicator.setLogin("maksym.rossiitsev@avispl.com");
        communicator.setPassword("");
        communicator.init();
    }

    @Test
    public void testGetMultupleStatistics() throws Exception {
        List<Statistics> statistics = communicator.getMultipleStatistics();
        Assertions.assertNotNull(statistics);
    }

    @Test
    public void testGetMultupleStatisticsWithAudit() throws Exception {
        communicator.setAuditEventResourceTypeFilter("");
//        communicator.setAuditEventSourceFilter("PlatformManager");
//        communicator.setAuditEventActionTypeFilter("Starting");
        // TODO make number of events configurable
        List<Statistics> statistics = communicator.getMultipleStatistics();
        Assertions.assertNotNull(statistics);
    }

    @Test
    public void testRetrieveMultipleStatistics() throws Exception {
        List<AggregatedDevice> statistics = communicator.retrieveMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();
        Thread.sleep(30000);
        statistics = communicator.retrieveMultipleStatistics();
        communicator.getMultipleStatistics();

        Assertions.assertNotNull(statistics);
    }

    @Test
    public void testIBControlOperationSleep () throws Exception {
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setDeviceId("");
        controllableProperty.setProperty("IBOperations#Sleep");
        controllableProperty.setValue("");
        communicator.controlProperty(controllableProperty);
    }

    @Test
    public void testIBControlOperationReboot () throws Exception {
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setDeviceId("BAC6A6AD839352D1FE7749119AB24856334132F59B46F61FA7F800D863EE4F00");
        controllableProperty.setProperty("IBOperations#Reboot");
        controllableProperty.setValue("");
        communicator.controlProperty(controllableProperty);
    }

    @Test
    public void testIBControlOperationShutdown () throws Exception {
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setDeviceId("");
        controllableProperty.setProperty("IBOperations#Shutdown");
        controllableProperty.setValue("");
        communicator.controlProperty(controllableProperty);
    }

    @Test
    public void testIBControlOperationHibernate () throws Exception {
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setDeviceId("");
        controllableProperty.setProperty("IBOperations#Hibernate");
        controllableProperty.setValue("");
        communicator.controlProperty(controllableProperty);
    }

    @Test
    public void testIBControlOperationAlert () throws Exception {
        ControllableProperty controllableProperty = new ControllableProperty();
        controllableProperty.setDeviceId("BAC6A6AD839352D1FE7749119AB24856334132F59B46F61FA7F800D863EE4F00");
        controllableProperty.setProperty("IBOperations#Alert");
        controllableProperty.setValue("GET BACK TO WORK");
        communicator.controlProperty(controllableProperty);
    }
}
