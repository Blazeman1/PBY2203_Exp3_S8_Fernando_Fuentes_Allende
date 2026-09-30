# Ver config-server/Dockerfile para la justificacion completa del build multi-stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
# curl: usado por el healthcheck de docker-compose (/actuator/health).
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/target/auth-server.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
