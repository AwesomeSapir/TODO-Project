FROM openjdk:latest
ADD target/HttpSpringServer-0.0.1-SNAPSHOT.jar /usr/server/Server.jar
WORKDIR /
EXPOSE 9285
CMD java -jar ./usr/server/Server.jar
