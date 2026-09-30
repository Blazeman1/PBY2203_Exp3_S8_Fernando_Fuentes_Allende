# Ver config-server/Dockerfile para la justificacion completa del build multi-stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
# curl: usado por el healthcheck de docker-compose (/actuator/health via HTTPS autofirmado).
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/target/bff-web.jar app.jar
# HTTPS (keystore autofirmado empaquetado dentro del JAR, ver application.yml): el puerto que se
# expone y publica es el TLS, igual que en ejecucion local.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
