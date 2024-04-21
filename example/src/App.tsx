import * as React from 'react';

import { StyleSheet, View, Text, Button } from 'react-native';
import {
  DataUsageEventTypes,
  addListener,
  getNetworkType,
  getWifiUsageStats,
  isConnected,
  requestPermissions,
} from 'react-native-data-usage';

export default function App() {
  const [networkType, setNetworkType] = React.useState<string | undefined>();
  const [bytes, setBytes] = React.useState(0);
  const [KB, setKB] = React.useState(0);
  const [MB, setMB] = React.useState(0);
  const [GB, setGB] = React.useState(0);
  const [connected, setIsConnected] = React.useState<boolean>(false);

  React.useEffect(() => {
    handleGetNetworkType();
    const connListener = addListener(
      DataUsageEventTypes.CONNECTION_CAHNGE,
      (event) => {
        console.log(event);
      }
    );
    const usageListener = addListener(
      DataUsageEventTypes.USAGE_CHANGE,
      (event) => {
        console.log(event);
      }
    );

    return () => {
      connListener.remove();
      usageListener.remove();
    };
  }, []);

  React.useEffect(() => {
    requestPermissions().then((permission) => console.log({ permission }));
  }, []);

  const handleGetNetworkType = () => {
    isConnected().then(setIsConnected);

    getNetworkType()
      .then(setNetworkType)
      .catch((e: any) => {
        setNetworkType('Unknown');
        console.log(e.message);
      });
  };

  const getDataUsage = async () => {
    const date = new Date();
    date.setHours(date.getHours() - 1);
    const time = date.getTime();
    const data = await getWifiUsageStats(time, Date.now());
    const kb = Math.floor((data / 1024) * 100) / 100;
    const mb = Math.floor((kb / 1024) * 1000) / 1000;
    const gb = Math.floor((mb / 1024) * 1000) / 1000;
    setBytes(data);
    setKB(kb);
    setMB(mb);
    setGB(gb);
  };

  const loadNetwork = () => {
    fetch('https://www.pickriders.com')
      .then((res) => res.text())
      .then(console.log);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.text}>Is Connected: {`${connected}`}</Text>

      <Text style={styles.text}>Network Type: {networkType}</Text>

      <Button title="Get Network Type" onPress={handleGetNetworkType} />

      <Text style={styles.text}>Loaded Network: {networkType}</Text>

      <Button title="Load Network" onPress={loadNetwork} />

      <Text style={styles.text}>Data Usage(Bytes): {bytes}b</Text>
      <Text style={styles.text}>Data Usage(KB): {KB}kb</Text>
      <Text style={styles.text}>Data Usage(MB): {MB}mb</Text>
      <Text style={styles.text}>Data Usage(GB): {GB}gb</Text>

      <Button title="Get Data Usage" onPress={getDataUsage} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
  text: {
    color: '#000',
    fontSize: 20,
    marginVertical: 10,
  },
});
