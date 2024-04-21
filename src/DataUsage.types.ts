export enum NetworkType {
  WIFI = 'WIFI',
  MOBILE = 'MOBILE',
}

export enum DataUsageEventTypes {
  USAGE_CHANGE = 'usageChange',
  CONNECTION_CAHNGE = 'connectionChange',
}

export type DataUsageEventResponse = {
  [DataUsageEventTypes.CONNECTION_CAHNGE]: {
    isConnected: boolean;
  };
  [DataUsageEventTypes.USAGE_CHANGE]: {
    networkType: number;
    eventName: string;
  };
};

/**
 * Returns the type of network that is connected.
 *
 * @returns NetworkType
 */
export type GetNetwokReturnType = Promise<NetworkType>;

/**
 * Rreturns true if the device is connected to a network, false otherwise.
 * @returns boolean
 */
export type IsConnectedReturnType = Promise<boolean>;
