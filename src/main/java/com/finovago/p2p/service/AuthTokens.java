package com.finovago.p2p.service;

/**
 * Tokens issued by a session-starting operation. Internal to the service layer: the controller decides
 * where each token goes (access token in the JSON body, refresh token only in the refresh_token cookie).
 */
public record AuthTokens(String accessToken, String refreshToken) {}
