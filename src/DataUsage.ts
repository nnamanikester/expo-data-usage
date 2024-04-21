import { NativeModules, Platform, NativeEventEmitter } from 'react-native';
import {
  requestMultiple,
  PERMISSIONS,
  RESULTS,
} from 'react-native-permissions';

import {
  DataUsageEventTypes,
  NetworkType,
  type DataUsageEventResponse,
  type GetNetwokReturnType,
  type IsConnectedReturnType,
} from './DataUsage.types';

const LINKING_ERROR =
  `The package 'react-native-data-usage' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const DataUsage = NativeModules.DataUsage
  ? NativeModules.DataUsage
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const emitter = new NativeEventEmitter(DataUsage);

export const getNetworkType = async (): GetNetwokReturnType => {
  try {
    const type: number = await DataUsage.getNetworkType();
    switch (type) {
      case 0:
        return NetworkType.MOBILE;
      case 1:
        return NetworkType.WIFI;
      default:
        throw new Error('Unknown network type');
    }
  } catch (e: any) {
    throw new Error(e.message);
  }
};

export const isConnected = async (): IsConnectedReturnType => {
  try {
    return await DataUsage.isConnected();
  } catch (e: any) {
    throw new Error(e.message);
  }
};

export const addListener = <E extends DataUsageEventTypes>(
  eventType: E,
  listener: (event: DataUsageEventResponse[E]) => void
) => {
  return emitter.addListener(eventType, listener);
};

export const removeAllListeners = (event: DataUsageEventTypes) => {
  return emitter.removeAllListeners(event);
};

/**
 * Returns the amount of data transferred through WIFI connection between `startTime` and `endTime`
 *
 * @param startTime `number` defines the start of the time range to get data usage
 * @param endTime  `number` defines the end of the time range to get data usage
 * @returns `number` amount of bytes transferred between `startTime` and `endTime`
 */
export const getWifiUsageStats = async (
  startTime: number,
  endTime: number
): Promise<number> => {
  try {
    if (!startTime || !endTime)
      throw new Error('Start and end time are required');
    if (startTime > endTime)
      throw new Error('Start time cannot be greater than end time');
    if (typeof startTime !== 'number' || typeof endTime !== 'number')
      throw new Error('Start and end time must be numbers');

    const tx = await DataUsage.getAllTxBytesWifi(
      startTime.toString(),
      endTime.toString()
    );
    const rx = await DataUsage.getAllRxBytesWifi(
      startTime.toString(),
      endTime.toString()
    );
    return Number(tx) + Number(rx);
  } catch (e: any) {
    console.warn(e.message);
    throw new Error(e.message);
  }
};

// (async () => {
//   try {
//     console.log('Called');
//     const network = await DataUsage.getDataUsageForWifiConnection(
//       Date.now().toString()
//     );
//     console.log({ network }, 'Finished');
//   } catch (e: any) {
//     console.log(e.message);
//   }
// })();

export const requestPermissions = async () => {
  const granted = await requestMultiple([PERMISSIONS.ANDROID.READ_PHONE_STATE]);

  return granted['android.permission.READ_PHONE_STATE'] === RESULTS.GRANTED;
};
