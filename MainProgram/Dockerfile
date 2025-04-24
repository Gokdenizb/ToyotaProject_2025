# build stage
FROM maven:3.8.7-amazoncorretto-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# runtime stage
FROM amazoncorretto:17-alpine-jdk
ARG JAR_FILE=target/*.jar
COPY --from=build /app/${JAR_FILE} /app/main-app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/main-app.jar"]
