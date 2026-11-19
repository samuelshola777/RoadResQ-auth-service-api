package com.roadresq.auth.notification;

public record NotificationDeviceRequest(
        String platform,
        String deviceModel,
        String osVersion,
        String appVersion,
        String pushPermission,
        String fcmToken
) {}