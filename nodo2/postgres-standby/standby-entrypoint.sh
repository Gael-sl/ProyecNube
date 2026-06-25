#!/bin/bash
set -e

echo "Iniciando configuración del servidor standby."
PGDATA="${PGDATA:-/var/lib/postgresql/data}"

PRIMARY_HOST="${PRIMARY_HOST:-postgres-primary}"
PRIMARY_PORT="${PRIMARY_PORT:-5432}"

# Esperar a que el servidor primario esté listo
until pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U postgres; do
  echo "Esperando a que $PRIMARY_HOST:$PRIMARY_PORT esté listo."
  sleep 2
done

# Si el directorio de datos esta vacío, realizar pg_basebackup
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "El directorio de datos está vacío. Realizando pg_basebackup."
  rm -rf "$PGDATA"/*
  PGPASSWORD=replpass pg_basebackup -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -D "$PGDATA" -U replicator -v -P -X stream -R
  echo "Copia de seguridad completada. Configuración de standby generada."
  chmod 700 "$PGDATA"
fi

# Iniciar postgres en segundo plano
echo "Iniciando el servidor de base de datos standby."
postgres -D "$PGDATA" &

# Iniciar ciclo de monitoreo
echo "Iniciando el monitor del servidor primario."
while true; do
  sleep 5
  # Si standby.signal no existe, significa que este nodo ya ha sido promovido
  if [ ! -f "$PGDATA/standby.signal" ]; then
    echo "El standby ya ha sido promovido o se está ejecutando como primario."
    break
  fi

  # Comprobar si el primario es alcanzable
  if ! pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U postgres; then
    echo "$PRIMARY_HOST:$PRIMARY_PORT no responde. Comprobando de nuevo en 5s."
    sleep 5
    if [ ! -f "$PGDATA/standby.signal" ] && [ ! -f "$PGDATA/recovery.conf" ]; then
       # Doble comprobación de si fue promovido en el transcurso de la espera
       break
    fi
    if ! pg_isready -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U postgres; then
      echo "¡Se ha confirmado la caída de $PRIMARY_HOST:$PRIMARY_PORT! Promoviendo standby a primario."
      pg_ctl promote -D "$PGDATA"
      break
    fi
  fi
done

wait
