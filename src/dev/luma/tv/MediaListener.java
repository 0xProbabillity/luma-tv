package dev.luma.tv;

import android.service.notification.NotificationListenerService;

/** Exists only so Android lets the Now Playing widget see media sessions (MediaSessionManager requires an enabled
 *  notification listener). It overrides nothing: Luma never reads, keeps or dismisses notifications. */
public final class MediaListener extends NotificationListenerService {}
