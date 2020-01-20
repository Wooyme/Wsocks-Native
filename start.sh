#!/bin/bash
rm -f nohup.out
PROCESS=`ps -ef | grep wsocks | grep -v grep | awk '{ print $2 }'`
echo $PROCESS
kill -9 $PROCESS
nohup java -Dvertx.disableDnsResolver=true -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=10 -XX:MaxDirectMemorySize=24m -Djdk.nio.maxCachedBufferSize=262144 -Djava.net.preferIPv4Stack=true -jar wsocks.jar config.json &

