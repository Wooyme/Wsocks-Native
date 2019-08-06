Wsocks Native
==
Different from the kotlin native version. This version rely on Graalvm to build native image.

Building
==

Make sure u install maven first, if not u can also use the mvnw. (but I prefer to install the global maven.

First of all, install the `common` module
```
cd common
mvn clean install -DskipTests
```

To package `client-core`
```
cd clientcore
mvn clean package -DskipTests
```
and then u get the *client.jar*

U can simply run client-core.jar, which is a no-gui client. To configure it, just open ur browser, and request
**http://localhost:1088/index?user=YOUR_USERNAME&pass=YOUR_PASSWORD&host=REMOTE_HOST&port=REMOTE_PORT**

To build a native image, U need to install graalvm-19.1.1 first and make sure the graal in the same location as **clientcore/build-native-image.sh** need.

after that,u can run the script and it will build the client-core for u.

Notes: *To avoid that the client-core spend too much memory, u'd better add the argument -Xmx128m when running it*

Before here, u can do every operation on Windows,Linux or Mac. And only Linux is supported to build the gui-client.

To build a native image of the gui-client, U need to clone the repository, `https://github.com/Wooyme/openjdk-jfx`, 
which is forked from openjdk-jfx and changed some code by myself.

After build openjdk-jfx, please copy all files under `build/sdk/rt/lib` to the location of ur jre.

and u can package the client and run `build-native-image.sh` to build a native image.

To make the client work, u need to copy the no-gui client to the same location of the gui one.

Help
==

* https://vertx.io/docs/[Vert.x Documentation]
* https://stackoverflow.com/questions/tagged/vert.x?sort=newest&pageSize=15[Vert.x Stack Overflow]
* https://groups.google.com/forum/?fromgroups#!forum/vertx[Vert.x User Group]
* https://gitter.im/eclipse-vertx/vertx-users[Vert.x Gitter]


