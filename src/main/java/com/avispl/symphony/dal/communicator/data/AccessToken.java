/*
 * Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * AccessToken class, stores all token details - type, expiration time, issuer user name etc
 *
 * @author Maksym.Rossiitsev/Symphony Team
 * @since 1.0.0
 * */
public class AccessToken {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("token_type")
    private String tokenType;
    @JsonProperty("expires_in")
    private String expiresIn;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("mfaRequired")
    private String mfaRequired;
    @JsonProperty(".issued")
    private Date issued;
    @JsonProperty(".expires")
    private Date expires;

    /**
     * Retrieves {@link #accessToken}
     *
     * @return value of {@link #accessToken}
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Sets {@link #accessToken} value
     *
     * @param accessToken new value of {@link #accessToken}
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Retrieves {@link #tokenType}
     *
     * @return value of {@link #tokenType}
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Sets {@link #tokenType} value
     *
     * @param tokenType new value of {@link #tokenType}
     */
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    /**
     * Retrieves {@link #expiresIn}
     *
     * @return value of {@link #expiresIn}
     */
    public String getExpiresIn() {
        return expiresIn;
    }

    /**
     * Sets {@link #expiresIn} value
     *
     * @param expiresIn new value of {@link #expiresIn}
     */
    public void setExpiresIn(String expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * Retrieves {@link #userName}
     *
     * @return value of {@link #userName}
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Sets {@link #userName} value
     *
     * @param userName new value of {@link #userName}
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Retrieves {@link #mfaRequired}
     *
     * @return value of {@link #mfaRequired}
     */
    public String getMfaRequired() {
        return mfaRequired;
    }

    /**
     * Sets {@link #mfaRequired} value
     *
     * @param mfaRequired new value of {@link #mfaRequired}
     */
    public void setMfaRequired(String mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    /**
     * Retrieves {@link #issued}
     *
     * @return value of {@link #issued}
     */
    public Date getIssued() {
        return issued;
    }

    /**
     * Sets {@link #issued} value
     *
     * @param issued new value of {@link #issued}
     */
    public void setIssued(Date issued) {
        this.issued = issued;
    }

    /**
     * Retrieves {@link #expires}
     *
     * @return value of {@link #expires}
     */
    public Date getExpires() {
        return expires;
    }

    /**
     * Sets {@link #expires} value
     *
     * @param expires new value of {@link #expires}
     */
    public void setExpires(Date expires) {
        this.expires = expires;
    }
}
