#!/bin/bash
set -e

echo "Iniciando configuración del servidor standby..."
PGDATA="${PGDATA:-/var/lib/postgresql/data}"

# Esperar a que el servidor primario esté listo
until pg_isready -h postgres-primary -p 5432 -U postgres; do
  echo "Esperando a que postgres-primary esté listo..."
  sleep 2
done

# Si el directorio de datos está vacío, realizar pg_basebackup
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "El directorio de datos está vacío. Realizando pg_basebackup..."
  rm -rf "$PGDATA"/*
  PGPASSWORD=replpass pg_basebackup -h postgres-primary -D "$PGDATA" -U replicator -v -P -X stream -R
  echo "Copia de seguridad completada. Configuración de standby generada."
  chmod 700 "$PGDATA"
fi

# Iniciar postgres en segundo plano
echo "Iniciando el servidor de base de datos standby..."
postgres -D "$PGDATA" &

# Iniciar ciclo de monitoreo
echo "Iniciando el monitor del servidor primario..."
while true; do
  sleep 5
  # Si standby.signal no existe, significa que este nodo ya ha sido promovido
  if [ ! -f "$PGDATA/standby.signal" ]; then
    echo "El standby ya ha sido promovido o se está ejecutando como primario. Deteniendo monitor."
    break
  fi

  # Comprobar si el primario es alcanzable
  if ! pg_isready -h postgres-primary -p 5432 -U postgres; then
    echo "postgres-primary no responde. Comprobando de nuevo en 5s..."
    sleep 5
    if [ ! -f "$PGDATA/standby.signal" ] && [ ! -f "$PGDATA/recovery.conf" ]; then
       # Doble comprobación de si fue promovido en el transcurso de la espera
       break
    fi
    if ! pg_isready -h postgres-primary -p 5432 -U postgres; then
      echo "¡Se ha confirmado la caída de postgres-primary! Promoviendo standby a primario..."
      pg_ctl promote -D "$PGDATA"
      break
    fi
  fi
done

# Esperar a que el proceso postgres en segundo plano finalice
wait
