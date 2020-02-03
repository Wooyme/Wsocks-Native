#!/bin/bash
java -Dvertx.disableDnsResolver=true -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=10 -XX:MaxDirectMemorySize=24m -Djdk.nio.maxCachedBufferSize=262144 -Djava.net.preferIPv4Stack=true -jar wsocks.jar

