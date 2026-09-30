# Build multi-stage (Semana 8): la imagen final NO contiene Maven ni el codigo fuente, solo el
# JAR ya empaquetado y un JRE minimo para ejecutarlo -mas liviana y con menor superficie de
# ataque que empaquetar el JDK completo o el arbol de fuentes en produccion.
#
# Cada modulo de este proyecto es independiente (pom.xml propio, sin depender del agregador raiz
# ni de otros modulos -ver banco-xyz-bff/pom.xml), asi que este Dockerfile solo necesita EL
# CONTENIDO DE ESTA CARPETA como contexto de build: no hace falta construir el resto del
# monorepo para levantar config-server.

# ---- Etapa 1: compilar con Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Etapa 2: solo el JRE + el JAR resultante ----
FROM eclipse-temurin:21-jre-alpine
# curl: usado por el healthcheck de docker-compose (/actuator/health).
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /build/target/config-server.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
