#!/bin/bash
set -e

echo "Configurando el usuario de réplica y permisos de hosts..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE replicator WITH REPLICATION PASSWORD 'replpass' LOGIN;
EOSQL

echo "host replication replicator 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
# Recargar la configuración de postgres para aplicar cambios en pg_hba.conf
pg_ctl reload
