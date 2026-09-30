#!/usr/bin/env bash
# Genera evidencia de que el stack COMPLETO corre 100% contenedorizado (Semana 8): construye las
# 8 imagenes Docker (una por microservicio, multi-stage, desde el codigo fuente -ver el
# Dockerfile de cada modulo), levanta todo con docker-compose.yaml (Kafka incluido), espera a que
# docker-compose reporte "healthy" en TODOS los servicios, y reutiliza SIN CAMBIOS las mismas
# pruebas end-to-end que ya usa la evidencia de la Semana 6/7 (scripts/probar_apis.sh,
# scripts/probar_transferencias.sh): los puertos publicados hacia el host son identicos a los de
# "java -jar" en el host, asi que esos scripts no distinguen si les responde un proceso local o un
# contenedor.
#
# Complementa (no reemplaza) a scripts/generar_evidencia.sh: aquel usa el modo hibrido de la
# Semana 7 (solo Kafka en Docker, el resto con "java -jar" en el host); este usa el
# docker-compose.yaml completo de la Semana 8 (los 8 microservicios + Kafka, todos en
# contenedores). Ambos escenarios son validos y se documentan por separado en el README.
#
# Requiere: Docker (Desktop en Windows/macOS, o Docker Engine en Linux) corriendo, curl, jq.
# Puertos libres 8080-8085, 8761, 8888, 9000, 9094 (9092 de Kafka queda solo dentro de la red de
# Docker, ver docker-compose.yaml: el trafico interno y el acceso desde el host usan listeners
# distintos a proposito).
#
# Uso:
#   bash scripts/generar_evidencia_docker.sh
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p evidencias
# Ver el comentario equivalente en scripts/generar_evidencia.sh: evidencias/*.log esta versionado
# a proposito, asi que sin este borrado una corrida fallida podria dejar mezclados sus .log
# nuevos con los de una corrida anterior ya comprometida a git.
rm -f evidencias/evidencia1[2-6]*.log

COMPOSE="docker compose -f docker-compose.yaml"

detener_todo() {
  echo
  echo "=== Deteniendo y limpiando el stack (docker compose down) ==="
  $COMPOSE down >/dev/null 2>&1 || true
}
trap detener_todo EXIT

if ! command -v docker >/dev/null 2>&1; then
  echo "No se encontro el comando 'docker'. Instala y abre Docker Desktop (Windows/macOS) o Docker Engine (Linux)." >&2
  exit 1
fi

echo "=== Construyendo las 8 imagenes Docker (multi-stage, desde el codigo fuente) ==="
$COMPOSE build 2>&1 | tee evidencias/evidencia12-docker-build.log

echo "=== Levantando el stack completo (docker-compose.yaml: 8 microservicios + Kafka) ==="
$COMPOSE up -d 2>&1 | tee evidencias/evidencia13-docker-up.log

echo "=== Esperando a que todos los servicios reporten 'healthy' ==="
TOTAL_SERVICIOS=$($COMPOSE config --services | wc -l | tr -d ' ')
esperar_healthy() {
  local intento
  for ((intento = 1; intento <= 60; intento++)); do
    local saludables
    saludables=$($COMPOSE ps | grep -c "(healthy)" || true)
    if [ "$saludables" -ge "$TOTAL_SERVICIOS" ]; then
      echo "Los $TOTAL_SERVICIOS servicios estan 'healthy' (intento $intento/60)."
      return 0
    fi
    echo "Saludables: $saludables/$TOTAL_SERVICIOS (intento $intento/60)..."
    sleep 5
  done
  echo "No todos los servicios llegaron a 'healthy' a tiempo." >&2
  $COMPOSE ps >&2
  return 1
}
esperar_healthy || exit 1

echo "=== Estado final del stack (docker compose ps) ==="
$COMPOSE ps | tee evidencias/evidencia14-docker-ps.log

echo "=== Ejecutando pruebas end-to-end (scripts/probar_apis.sh) contra el stack contenedorizado ==="
bash scripts/probar_apis.sh 2>&1 | tee evidencias/evidencia15-docker-pruebas-apis.log
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  echo "Las pruebas end-to-end fallaron, revisa evidencias/evidencia15-docker-pruebas-apis.log" >&2
  exit 1
fi

echo "=== Ejecutando pruebas de transferencias (Saga + Kafka) contra el stack contenedorizado ==="
bash scripts/probar_transferencias.sh 2>&1 | tee evidencias/evidencia16-docker-pruebas-transferencias.log
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  echo "Las pruebas de transferencias fallaron, revisa evidencias/evidencia16-docker-pruebas-transferencias.log" >&2
  exit 1
fi

echo
echo "=== Listo. Evidencia del stack Docker generada en evidencias/: ==="
ls -1 evidencias/evidencia1[2-6]*.log
