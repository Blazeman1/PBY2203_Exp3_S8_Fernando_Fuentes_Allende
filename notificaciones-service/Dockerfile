# Ver config-server/Dockerfile para la justificacion completa del build multi-stage.
#
# Una sola imagen sirve para las 2 instancias (A y B) del docker-compose: cual puerto e
# instance-id usa cada contenedor se decide en tiempo de ejecucion via la variable de entorno
# SERVER_PORT (ver application.yml), no en tiempo de build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
# curl: usado por el healthcheck de docker-compose (/actuator/health).
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/target/notificaciones-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
