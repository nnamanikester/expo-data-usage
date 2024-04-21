package com.datausage;

import android.annotation.TargetApi;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.telephony.TelephonyManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.module.annotations.ReactModule;

@TargetApi(Build.VERSION_CODES.M)
@ReactModule(name = DataUsageModule.NAME)
public class DataUsageModule extends ReactContextBaseJavaModule {
  public static final String NAME = "DataUsage";
  private final ReactApplicationContext reactContext;
  private ConnectivityManager connectivityManager;
  private ConnectivityManager.NetworkCallback networkCallback;
  private NetworkStatsManager networkStatsManager;
  private NetworkStatsManager.UsageCallback usageCallback;
  private int usageListenerCount = 0;
  private int connectionListenerCount = 0;
  int packageUid;
  private static final int READ_PHONE_STATE_REQUEST = 37;

  public DataUsageModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.packageUid = PackageManagerHelper.getPackageUid(reactContext);
    this.connectivityManager = (ConnectivityManager) reactContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    networkStatsManager = (NetworkStatsManager) reactContext.getSystemService(Context.NETWORK_STATS_SERVICE);
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  /**
   * Resolves true if the device is connected to the internet and false otherwise.
   */
  @ReactMethod
  public void isConnected(Promise promise) {
    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

    if (networkInfo != null)
      promise.resolve(networkInfo.isConnected());
    else
      promise.resolve(false);
  }

  /**
   * Resolves with Network Type int
   * `0` -> WIFI
   * `1` -> CELLULAR(MOBILE)
   */
  @ReactMethod
  public void getNetworkType(Promise promise) {
    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

    if (networkInfo == null)
      promise.reject("Not available.");
    else
      promise.resolve(networkInfo.getType());
  }

  /**
   * Registers event listeners to send event to the javascript thread.
   * (Called by React Native EventEmitter)
   *
   * @param eventName
   */
  @ReactMethod
  public void addListener(String eventName) {
    if ("connectionChange".equals(eventName)) {
      addConnectionListener();
    } else if ("usageChange".equals(eventName)) {
      addUsageListener();
    }
  }

  /**
   * Removes event from the javascript thread.
   * (Called by React Native EventEmitter)
   *
   * @param count
   */
  @ReactMethod
  public void removeListeners(int count) {
    removeConnectionListener(count);
    removeUsageListener(count);
  }

  private void addConnectionListener() {
    if (connectionListenerCount == 0) {
      if (connectivityManager != null) {
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
          @Override
          public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            // Emit event when network becomes available
            WritableMap params = Arguments.createMap();
            params.putBoolean("isConnected", true);
            sendEvent("connectionChange", params);
          }

          @Override
          public void onLost(@NonNull Network network) {
            super.onLost(network);
            // Emit event when network is lost
            WritableMap params = Arguments.createMap();
            params.putBoolean("isConnected", false);
            sendEvent("connectionChange", params);
          }
        };
        connectivityManager.registerNetworkCallback(request, networkCallback);
      }
      connectionListenerCount += 1;
    }
  }

  private void removeConnectionListener(int count) {
    connectionListenerCount -= count;
    if (connectionListenerCount == 0) {
      if (connectivityManager != null && networkCallback != null) {
        connectivityManager.unregisterNetworkCallback(networkCallback);
        networkCallback = null;
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.N)
  private void addUsageListener() {
    if (usageListenerCount == 0) {
      NetworkStatsManager.UsageCallback usageCallback = new NetworkStatsManager.UsageCallback() {
        @Override
        public void onThresholdReached(int networkType, String subscriberId) {
          WritableMap params = Arguments.createMap();
          params.putString("eventName", "usageThresholdReached");
          params.putInt("networkType", networkType);
          sendEvent("usageChange", params);
        }
      };
      networkStatsManager.registerUsageCallback(ConnectivityManager.TYPE_WIFI, "", 0, usageCallback);
      usageListenerCount += 1;
    }
  }

  private void removeUsageListener(int count) {
    usageListenerCount -= count;
    if (usageListenerCount == 0) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && usageCallback != null) {
        networkStatsManager.unregisterUsageCallback(usageCallback);
        usageCallback = null;
      }
    }
  }

  @ReactMethod
  public void getDataUsageForWifiConnection(String from, String to, Promise promise) throws RemoteException {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats networkStats = networkStatsManager.querySummary(
        ConnectivityManager.TYPE_WIFI,
        null,
        startTime,
        endTime);

    long totalWifiDataUsage = 0;

    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
    while (networkStats.hasNextBucket()) {
      networkStats.getNextBucket(bucket);

      totalWifiDataUsage += bucket.getRxBytes() + bucket.getTxBytes();
    }

    promise.resolve(Long.toString(totalWifiDataUsage));
  }

  /**
   * Resolves total number of bytes received within `from` and `to` time interval
   * using the mobile network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getAllRxBytesMobile(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats.Bucket bucket;
    try {
      bucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE,
          getSubscriberId(ConnectivityManager.TYPE_MOBILE),
          startTime,
          endTime);
    } catch (RemoteException e) {
      return;
    }
    promise.resolve(Long.toString(bucket.getRxBytes()));
  }

  /**
   * Resolves total number of bytes transferred within `from` and `to` time
   * interval using the mobile network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getAllTxBytesMobile(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats.Bucket bucket;
    try {
      bucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE,
          getSubscriberId(ConnectivityManager.TYPE_MOBILE),
          startTime, endTime);
    } catch (RemoteException e) {
      return;
    }
    promise.resolve(Long.toString(bucket.getTxBytes()));
  }

  /**
   * Resolves total number of bytes received within `from` and `to` time interval
   * using the wifi network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getAllRxBytesWifi(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats.Bucket bucket;
    try {
      bucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI,
          "",
          startTime,
          endTime);
    } catch (RemoteException e) {
      return;
    }
    promise.resolve(Long.toString(bucket.getRxBytes()));
  }

  /**
   * Resolves total number of bytes transferred within `from` and `to` time
   * interval using the wifi network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getAllTxBytesWifi(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats.Bucket bucket;
    try {
      bucket = networkStatsManager.querySummaryForDevice(ConnectivityManager.TYPE_WIFI,
          "",
          startTime,
          endTime);
    } catch (RemoteException e) {
      return;
    }
    promise.resolve(Long.toString(bucket.getTxBytes()));
  }

  /**
   * Resolves total number of bytes received by the current app within `from` and
   * `to` time interval using the mobile network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getPackageRxBytesMobile(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats networkStats = networkStatsManager.queryDetailsForUid(
        ConnectivityManager.TYPE_MOBILE,
        getSubscriberId(ConnectivityManager.TYPE_MOBILE),
        startTime,
        endTime,
        packageUid);

    long rxBytes = 0L;
    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
    while (networkStats.hasNextBucket()) {
      networkStats.getNextBucket(bucket);
      rxBytes += bucket.getRxBytes();
    }
    networkStats.close();

    promise.resolve(Long.toString(rxBytes));
  }

  /**
   * Resolves total number of bytes transferred by the current app within `from`
   * and `to` time interval using the mobile network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getPackageTxBytesMobile(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats networkStats = networkStatsManager.queryDetailsForUid(
        ConnectivityManager.TYPE_MOBILE,
        getSubscriberId(ConnectivityManager.TYPE_MOBILE),
        startTime,
        endTime,
        packageUid);

    long txBytes = 0L;
    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
    while (networkStats.hasNextBucket()) {
      networkStats.getNextBucket(bucket);
      txBytes += bucket.getTxBytes();
    }
    networkStats.close();

    promise.resolve(Long.toString(txBytes));
  }

  /**
   * Resolves total number of bytes received by the current app within `from` and
   * `to` time interval using the wifi network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getPackageRxBytesWifi(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats networkStats = networkStatsManager.queryDetailsForUid(
        ConnectivityManager.TYPE_WIFI,
        "",
        startTime,
        endTime,
        packageUid);

    long rxBytes = 0L;
    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
    while (networkStats.hasNextBucket()) {
      networkStats.getNextBucket(bucket);
      rxBytes += bucket.getRxBytes();
    }
    networkStats.close();

    promise.resolve(Long.toString(rxBytes));
  }

  /**
   * Resolves total number of bytes transferred by the current app within `from`
   * and `to` time interval using the wifi network.
   *
   * @param from start time
   * @param to   end time
   */
  @ReactMethod
  public void getPackageTxBytesWifi(String from, String to, Promise promise) {
    long startTime = Long.parseLong(from);
    long endTime = Long.parseLong(to);

    NetworkStats networkStats = networkStatsManager.queryDetailsForUid(
        ConnectivityManager.TYPE_WIFI,
        "",
        startTime,
        endTime,
        packageUid);

    long txBytes = 0L;
    NetworkStats.Bucket bucket = new NetworkStats.Bucket();
    while (networkStats.hasNextBucket()) {
      networkStats.getNextBucket(bucket);
      txBytes += bucket.getTxBytes();
    }
    networkStats.close();

    promise.resolve(Long.toString(txBytes));
  }

  /**
   * Get subscriber ID for requesting network usage history
   *
   * @param networkType
   * @return
   */
  private String getSubscriberId(int networkType) {
    if (ConnectivityManager.TYPE_MOBILE == networkType) {
      TelephonyManager tm = (TelephonyManager) reactContext.getSystemService(Context.TELEPHONY_SERVICE);
      return tm.getSubscriberId();
    }
    return "";
  }

  /**
   * Sends event to the React Native thread.
   *
   * @param eventName
   * @param params
   */
  private void sendEvent(String eventName, @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }
}
