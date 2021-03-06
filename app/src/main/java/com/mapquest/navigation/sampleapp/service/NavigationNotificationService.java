package com.mapquest.navigation.sampleapp.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleRegistry;
import android.arch.lifecycle.LifecycleRegistryOwner;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.mapquest.navigation.NavigationManager;
import com.mapquest.navigation.location.LocationProviderAdapter;
import com.mapquest.navigation.sampleapp.BuildConfig;
import com.mapquest.navigation.sampleapp.MQNavigationSampleApplication;
import com.mapquest.navigation.sampleapp.R;
import com.mapquest.navigation.sampleapp.activity.NavigationActivity;
import com.mapquest.navigation.sampleapp.tts.TextToSpeechPromptListenerManager;
import com.mapquest.navigation.internal.NavigationManagerImpl;
import com.mapquest.navigation.internal.util.LogUtil;
import com.mapquest.navigation.listener.NavigationLifecycleEventListener;
import com.mapquest.navigation.model.Route;
import com.mapquest.navigation.model.RouteStoppedReason;
import com.mapquest.navigation.model.location.Coordinate;

public class NavigationNotificationService extends Service implements LifecycleRegistryOwner {

    private static final String TAG = LogUtil.generateLoggingTag(NavigationNotificationService.class);
    private static final int NOTIFICATION_ID = 1;

    private NavigationManager mNavigationManager;
    private IBinder mBinder = new LocalBinder();
    private Route mRoute;
    private TextToSpeechPromptListenerManager mTextToSpeechPromptListenerManager;
    private LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");

        //
        // Note that START_STICKY seems to be preferred for Services that continue to run in the background
        // after they have handled requests. However, if the process is killed, we DON'T want the
        // service to be recreated automatically... without a way to actually restore navigation.
        // Therefore, we return START_NOT_STICKY here. :)
        //
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");

        // note: return true only if we want service's onRebind() method later called when new clients bind to it
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mTextToSpeechPromptListenerManager = new TextToSpeechPromptListenerManager(this, getLifecycle());
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);

        mNavigationManager.deinitialize();
        mNavigationManager = null;

        clearNotification();
        super.onDestroy();
    }

    public NavigationManager getNavigationManager() {
        // create and initialize singleton NavigationManager instance (w/ location-adapter) as needed
        if(mNavigationManager == null) {
            LocationProviderAdapter locationProviderAdapter = ((MQNavigationSampleApplication) getApplication()).getLocationProviderAdapter();
            mNavigationManager = createNavigationManager(locationProviderAdapter);

            mNavigationManager.initialize();

            mTextToSpeechPromptListenerManager.initialize(mNavigationManager);
        }
        return mNavigationManager;
    }

    public void setRoute(Route route) {
        mRoute = route;
    }

    public void updateNotification(Notification notification) {
        startForeground(NOTIFICATION_ID, notification);
    }

    public void clearNotification() {
        stopForeground(true);
    }

    public static NavigationNotificationService fromBinder(IBinder binder) {
        if(binder instanceof LocalBinder) {
            return ((LocalBinder)binder).getService();
        } else {
            throw new IllegalArgumentException("Given IBinder is not an instance of the expected implementation type.");
        }
    }

    private NavigationManager createNavigationManager(LocationProviderAdapter locationProviderAdapter) {
        Log.d(TAG, "createNavigationManager() locationProviderAdapter: " + locationProviderAdapter);

        mNavigationManager = new NavigationManager.Builder(this, BuildConfig.API_KEY, locationProviderAdapter).build();

        ((NavigationManagerImpl) mNavigationManager).addNavigationLifecycleEventListener(
                new NotificationUpdatingNavigationLifecycleEventListener());

        return mNavigationManager;
    }

    @Override
    public LifecycleRegistry getLifecycle() {
        return mLifecycleRegistry;
    }

    private class LocalBinder extends Binder {
        public NavigationNotificationService getService() {
            return NavigationNotificationService.this;
        }
    }

    private class NotificationUpdatingNavigationLifecycleEventListener implements NavigationLifecycleEventListener {
        @Override
        public void onRouteAccepted(Route route) { }

        @Override
        public void onNavigationStarting() {
            updateNotification(buildNotification("Starting navigation"));
        }

        @Override
        public void onNavigationStarted() {
            updateNotification(buildNotification("Navigation in progress"));
        }

        @Override
        public void onNavigationPaused() {
            updateNotification(buildNotification("Navigation paused"));
        }

        @Override
        public void onNavigationResumed() {
            updateNotification(buildNotification("Navigation in progress"));
        }

        @Override
        public void onOffRoute(Coordinate observedLocation) {
            updateNotification(buildNotification("Off-route"));
        }

        @Override
        public void onNavigationStopped(RouteStoppedReason routeStoppedReason) {
            clearNotification();
        }
    }

    private Notification buildNotification(String description) {
        return new Notification.Builder(this)
                .setOngoing(true)
                .setContentTitle("Navigating")
                .setContentText(description)
                .setSmallIcon(R.drawable.circle)
                .setContentIntent(buildNavPendingIntent())
                .build();
    }

    private PendingIntent buildNavPendingIntent() {
        if (mRoute == null) {
            throw new IllegalStateException("mRoute must be set before a pending intent is created");
        }

        Intent navigationActivityIntent = new Intent(this, NavigationActivity.class);
        navigationActivityIntent.putExtra(NavigationActivity.ROUTE_KEY, mRoute);
        navigationActivityIntent.setAction("Notification"); // This is necessary for passing along the extras set above

        return PendingIntent.getActivity(this, 0, navigationActivityIntent, 0);
    }
}
