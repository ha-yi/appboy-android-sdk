package com.braze.push;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.appboy.Constants;
import com.appboy.models.push.BrazeNotificationPayload;
import com.braze.Braze;
import com.braze.IBrazeNotificationFactory;
import com.braze.configuration.BrazeConfigurationProvider;
import com.braze.support.BrazeLogger;

public class BrazeAmazonDeviceMessagingReceiver extends BroadcastReceiver {
  private static final String TAG = BrazeLogger.getBrazeLogTag(BrazeAmazonDeviceMessagingReceiver.class);
  private static final String ADM_RECEIVE_INTENT_ACTION = "com.amazon.device.messaging.intent.RECEIVE";
  private static final String ADM_REGISTRATION_INTENT_ACTION = "com.amazon.device.messaging.intent.REGISTRATION";
  private static final String ADM_ERROR_KEY = "error";
  private static final String ADM_ERROR_DESCRIPTION_KEY = "error_description";
  private static final String ADM_REGISTRATION_ID_KEY = "registration_id";
  private static final String ADM_UNREGISTERED_KEY = "unregistered";
  private static final String ADM_MESSAGE_TYPE_KEY = "message_type";
  private static final String ADM_DELETED_MESSAGES_KEY = "deleted_messages";
  private static final String ADM_NUMBER_OF_MESSAGES_DELETED_KEY = "total_deleted";

  @Override
  public void onReceive(Context context, Intent intent) {
    handleReceivedIntent(context, intent);
  }

  public static void handleReceivedIntent(Context context, Intent intent) {
    if (intent == null) {
      BrazeLogger.w(TAG, "Received null intent. Doing nothing.");
      return;
    }
    Context applicationContext = context.getApplicationContext();
    PushHandlerRunnable pushHandlerRunnable = new PushHandlerRunnable(applicationContext, intent);
    new Thread(pushHandlerRunnable).start();
  }

  private static class PushHandlerRunnable implements Runnable {
    private final String mAction;
    private final Context mApplicationContext;
    private final Intent mIntent;

    PushHandlerRunnable(Context applicationContext, @NonNull Intent intent) {
      mApplicationContext = applicationContext;
      mIntent = intent;
      mAction = intent.getAction();
    }

    @Override
    public void run() {
      try {
        performWork();
      } catch (Exception e) {
        BrazeLogger.e(TAG, "Caught exception while performing the push "
            + "notification handling work. Action: " + mAction + " Intent: " + mIntent, e);
      }
    }

    private void performWork() {
      BrazeLogger.i(TAG, "Received broadcast message. Message: " + mIntent.toString());
      String action = mIntent.getAction();
      if (ADM_REGISTRATION_INTENT_ACTION.equals(action)) {
        handleRegistrationEventIfEnabled(new BrazeConfigurationProvider(mApplicationContext), mApplicationContext, mIntent);
      } else if (ADM_RECEIVE_INTENT_ACTION.equals(action)) {
        handleAppboyAdmMessage(mApplicationContext, mIntent);
      } else if (Constants.APPBOY_CANCEL_NOTIFICATION_ACTION.equals(action)) {
        BrazeNotificationUtils.handleCancelNotificationAction(mApplicationContext, mIntent);
      } else if (Constants.APPBOY_ACTION_CLICKED_ACTION.equals(action)) {
        BrazeNotificationActionUtils.handleNotificationActionClicked(mApplicationContext, mIntent);
      } else if (Constants.APPBOY_PUSH_CLICKED_ACTION.equals(action)) {
        BrazeNotificationUtils.handleNotificationOpened(mApplicationContext, mIntent);
      } else if (Constants.APPBOY_PUSH_DELETED_ACTION.equals(action)) {
        BrazeNotificationUtils.handleNotificationDeleted(mApplicationContext, mIntent);
      } else {
        BrazeLogger.w(TAG, "The ADM receiver received a message not sent from Appboy. Ignoring the message.");
      }
    }
  }

  /**
   * Processes the registration/unregistration result returned from the ADM servers. If the
   * registration/unregistration is successful, this will store/clear the registration ID from the
   * device. Otherwise, it will log an error message and the device will not be able to receive ADM
   * messages.
   */
  @VisibleForTesting
  static boolean handleRegistrationIntent(Context context, Intent intent) {
    String error = intent.getStringExtra(ADM_ERROR_KEY);
    String errorDescription = intent.getStringExtra(ADM_ERROR_DESCRIPTION_KEY);
    String registrationId = intent.getStringExtra(ADM_REGISTRATION_ID_KEY);
    String unregistered = intent.getStringExtra(ADM_UNREGISTERED_KEY);

    if (error != null) {
      BrazeLogger.w(TAG, "Error during ADM registration: " + error + " description: " + errorDescription);
    } else if (registrationId != null) {
      BrazeLogger.i(TAG, "Registering for ADM messages with registrationId: " + registrationId);
      Braze.getInstance(context).registerAppboyPushMessages(registrationId);
    } else if (unregistered != null) {
      BrazeLogger.w(TAG, "The device was un-registered from ADM: " + unregistered);
    } else {
      BrazeLogger.w(TAG, "The ADM registration intent is missing error information, registration id, and unregistration "
          + "confirmation. Ignoring.");
      return false;
    }
    return true;
  }

  /**
   * Handles both Braze data push ADM messages and notification messages. Notification messages are
   * posted to the notification center if the ADM message contains a title and body and the payload
   * is sent to the application via an Intent. Data push messages do not post to the notification
   * center, although the payload is forwarded to the application via an Intent as well.
   */
  @VisibleForTesting
  static boolean handleAppboyAdmMessage(Context context, Intent intent) {
    if (!BrazeNotificationUtils.isAppboyPushMessage(intent)) {
      return false;
    }

    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    String messageType = intent.getStringExtra(ADM_MESSAGE_TYPE_KEY);
    if (ADM_DELETED_MESSAGES_KEY.equals(messageType)) {
      int totalDeleted = intent.getIntExtra(ADM_NUMBER_OF_MESSAGES_DELETED_KEY, -1);
      if (totalDeleted == -1) {
        BrazeLogger.w(TAG, "Unable to parse ADM message. Intent: " + intent.toString());
      } else {
        BrazeLogger.i(TAG, "ADM deleted " + totalDeleted + " messages. Fetch them from Appboy.");
      }
      return false;
    } else {
      Bundle admExtras = intent.getExtras();
      BrazeLogger.d(TAG, "Push message payload received: " + admExtras);

      if (BrazeNotificationUtils.isUninstallTrackingPush(admExtras)) {
        // Note that this re-implementation of this method does not forward the notification to receivers.
        BrazeLogger.i(TAG, "Push message is uninstall tracking push. Doing nothing. Not forwarding this notification to broadcast receivers.");
        return false;
      }

      if (!admExtras.containsKey(Constants.APPBOY_PUSH_RECEIVED_TIMESTAMP_MILLIS)) {
        admExtras.putLong(Constants.APPBOY_PUSH_RECEIVED_TIMESTAMP_MILLIS, System.currentTimeMillis());
      }

      // Parsing the Braze data extras (data push).
      Bundle appboyExtras = BrazeNotificationPayload.getAttachedAppboyExtras(admExtras);
      admExtras.putBundle(Constants.APPBOY_PUSH_EXTRAS_KEY, appboyExtras);

      final BrazeConfigurationProvider appConfigurationProvider = new BrazeConfigurationProvider(context);
      final BrazeNotificationPayload payload = new BrazeNotificationPayload(context, appConfigurationProvider, admExtras);
      if (BrazeNotificationUtils.isNotificationMessage(intent)) {
        int notificationId = BrazeNotificationUtils.getNotificationId(payload);
        admExtras.putInt(Constants.APPBOY_PUSH_NOTIFICATION_ID, notificationId);

        Notification notification = createNotification(payload);
        if (notification == null) {
          BrazeLogger.d(TAG, "Notification created by notification factory was null. Not displaying notification.");
          return false;
        }

        notificationManager.notify(Constants.APPBOY_PUSH_NOTIFICATION_TAG, notificationId, notification);
        BrazeNotificationUtils.sendPushMessageReceivedBroadcast(context, admExtras);

        // Since we have received a notification, we want to wake the device screen.
        BrazeNotificationUtils.wakeScreenIfAppropriate(context, appConfigurationProvider, admExtras);

        // Set a custom duration for this notification.
        if (payload.getPushDuration() != null) {
          BrazeNotificationUtils.setNotificationDurationAlarm(context, BrazeAmazonDeviceMessagingReceiver.class, notificationId, payload.getPushDuration());
        }

        return true;
      } else {
        BrazeNotificationUtils.sendPushMessageReceivedBroadcast(context, admExtras);
        BrazeNotificationUtils.requestGeofenceRefreshIfAppropriate(context, admExtras);
        return false;
      }
    }
  }

  @VisibleForTesting
  static boolean handleRegistrationEventIfEnabled(BrazeConfigurationProvider appConfigurationProvider, Context context, Intent intent) {
    BrazeLogger.i(TAG, "Received ADM registration. Message: " + intent.toString());
    // Only handle ADM registration events if ADM registration handling is turned on in the
    // configuration file.
    if (appConfigurationProvider.isAdmMessagingRegistrationEnabled()) {
      BrazeLogger.d(TAG, "ADM enabled in braze.xml. Continuing to process ADM registration intent.");
      handleRegistrationIntent(context, intent);
      return true;
    }
    BrazeLogger.w(TAG, "ADM not enabled in braze.xml. Ignoring ADM registration intent. Note: you must set "
        + "com_appboy_push_adm_messaging_registration_enabled to true in your braze.xml to enable ADM.");
    return false;
  }

  @SuppressWarnings("deprecation") // createNotification() with old method
  private static Notification createNotification(BrazeNotificationPayload payload) {
    BrazeLogger.v(TAG, "Creating notification with payload:\n" + payload);
    IBrazeNotificationFactory appboyNotificationFactory = BrazeNotificationUtils.getActiveNotificationFactory();
    Notification notification = appboyNotificationFactory.createNotification(payload);
    if (notification == null) {
      BrazeLogger.d(TAG, "Calling older notification factory method after null notification returned on newer method");
      // Use the older factory method on null. Potentially only the one method is implemented
      notification = appboyNotificationFactory.createNotification(payload.getAppboyConfigurationProvider(),
          payload.getContext(),
          payload.getNotificationExtras(),
          payload.getAppboyExtras());
    }

    return notification;
  }
}

