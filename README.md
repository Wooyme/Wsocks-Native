Wsocks Native
===
Different from the kotlin native version. This version rely on Graalvm to build native image.

更新
======
2018-8-26: 底层换成kcp

使用说明
====
整体分成三个部分，客户端、中心和服务端。目前中心还是个假的中心，没什么功能，以后打算把一些有意思的功能加进去

中心
---
启动非常简单  
java
```
java -jar center-1.0.0-SNAPSHOT-jar-with-dependencies.jar [udp端口] [http端口] [管理员密码] [代理接入密码] [salt]
```
native
```
./center [udp端口] [http端口] [管理员密码] [代理接入密码] [salt]
```

服务端
---
**启动PCAP需要root**  
java
```
sudo java -Xmx256m -jar wsocks-1.0.0-SNAPSHOT-jar-with-dependencies.jar config.json
```
native
```
sudo ./wsocks config.json -Xmx128m
```
配置文件config.json
```
{
    "lowendbox":是否为低性能机器,
    "server":"接收服务udp|pcap",
    "http":http端口号,
    "udp": udp端口号,
    "maxWaitSnd":8000,
    "WndSize": 256或128,
    "mtu": 1400,
    "host":"你的ip，或者别人的ip",
    "slave":"代理接入秘钥",
    "center":"中心url",
}
```
例如：
```
{
    "lowendbox":true,
    "server":"udp",
    "http":1889,
    "udp": 1890,
    "maxWaitSnd":8000,
    "WndSize": 256,
    "mtu": 1200,
    "host":"192.168.1.100",
    "slave":"urmyslave",
    "center":"http://192.168.1.100:7771"
}
```
稍微解释一下。首先是`lowendbox`，如果你的内存非常小，在256M以内，那么推荐设成`true`  
然后是接收协议，有三种选择，http、websocket、udp。其中http目前有很大的问题，基本上被舍弃了。websocket和udp可以都试试，虽然我自己测试下来差别不大(可能因为我自己系统上是有bbr的)，但是某些线路可能会有比较大的区别。

> 需要注意的是，wsocks目前采用一种有点诡异的思路，这个*接收服务*只是服务端接收客户端数据所采用的服务，而客户端接收服务端数据则只使用udp

`maxWaitSnd`和`WndSize`可以根据内存情况调整，我在192M内存的vps上测试8000和256都是没有问题的。

然后是`host`，这个事情也很诡异。经过测试，在大部分(3/4)kvm的vps上可以填*别人的ip*，但是某些服务商的vps只能填*自己的ip*。首先是为什么可以填别人的ip，因为实际上wsocks回传数据是走的**raw socket**,这也就是为什么需要root权限。虽然在一些论坛或者像stackoverflow这样的网站上往往可以看到一些言论认为伪造ip是不可能的，但是实际上这个不可能伪造是指不可能去伪造成指定的其他人，比如说我不可能在公网上伪造成google。但是在测试后发现，大部分情况下让自己不是自己还是做的到的。

如果你的ip是104.174.100.100，那么**大部分情况下**,你可以在104.174.1.1-104.174.255.255里任选一个ip。这种方式一定程度上可以保护服务端，并且在之后有配合center组成某种网络的计划。

客户端
----
客户端其实有两个部分，一个是ui，一个是core。两者之间是通过http通信的，实际上不开ui的部分完全是可以的。

需要环境变量
```
-Dcenter_url=[中心url] -Dcenter_host=[中心地址] -Dcenter_port=[中心端口] -Dsalt=[salt] -Dversion=[version]
```

其他
===
这个Native版的wsocks，一是为了优化客户端、毕竟本来运行300M内存是有点夸张了。二是让服务端能在更廉价的机器上运行，native化了之后服务端程序在192M内存的vps上运行无压力运行，而且在加入kcp之后让wsocks不再需要bbr或锐速，可以选择更加廉价的openvz。毕竟现在Virmach的openvz有最低10刀一年的方案(1刀一个月，年付10个月)，简直不要钱。
