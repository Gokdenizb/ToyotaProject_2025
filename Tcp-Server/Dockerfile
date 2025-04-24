FROM maven:3.8.7-amazoncorretto-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM amazoncorretto:17-alpine-jdk
ARG JAR_FILE=target/Tcp-Server-1.0-SNAPSHOT.jar
COPY --from=build /app/${JAR_FILE} /app/tcp-server.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/tcp-server.jar"]
