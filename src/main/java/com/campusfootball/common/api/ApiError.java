package com.campusfootball.common.api;

public record ApiError(String code, String message, String requestId) {
}
