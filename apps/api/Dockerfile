# syntax=docker/dockerfile:1

FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S scansettle && adduser -S scansettle -G scansettle
WORKDIR /app
COPY --from=build /build/target/api-*.jar app.jar
RUN chown -R scansettle:scansettle /app
USER scansettle

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
