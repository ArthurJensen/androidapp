package com.example.myapplication

import android.service.notification.NotificationListenerService

class MusicNotificationService : NotificationListenerService() {
    // This is just a proxy to let the system show the "Music Access" toggle.
    // All actual music logic stays in your main OverlayService.
}