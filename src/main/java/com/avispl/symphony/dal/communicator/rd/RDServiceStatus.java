/*
 * Copyright (c) 2025 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.rd;

public enum RDServiceStatus {
    DISABLED("Disabled"), READY("Ready"), FAILED("Failed");

    private String name;
    RDServiceStatus(String name) {
        this.name = name;
    }
    /**
     * Retrieves {@link #name}
     *
     * @return value of {@link #name}
     */
    public String getName() {
        return name;
    }
}
