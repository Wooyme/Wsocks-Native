= Wsocks

image:https://img.shields.io/badge/vert.x-3.6.3-purple.svg[link="https://vertx.io"]

This application was generated using http://start.vertx.io

== Building
Make sure u install maven first, if u don't u can also use the mvnw. I prefer to install the global maven.
First install the `common`
```
cd common
mvn clean install -DskipTests
```
To package client
```

cd clientcore
mvn clean package -DskipTests
cd ../client
mvn clean package -DskipTests
```
and then u get client.jar and client-core.jar.
U can simply run client-core.jar, which is a no-gui client, and the client.jar is rely on the client-core.

To package your application:
```
./mvnw clean package
```

To run your application:
```
./mvnw clean compile exec:java
```

== Help

* https://vertx.io/docs/[Vert.x Documentation]
* https://stackoverflow.com/questions/tagged/vert.x?sort=newest&pageSize=15[Vert.x Stack Overflow]
* https://groups.google.com/forum/?fromgroups#!forum/vertx[Vert.x User Group]
* https://gitter.im/eclipse-vertx/vertx-users[Vert.x Gitter]


