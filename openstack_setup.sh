#!/bin/bash
# =========================================================================
# Script de aprovisionamiento de infraestructura de Red (Neutron), Cómputo (Nova)
# y Almacenamiento (Cinder) mediante la consola de OpenStack CLI.
# =========================================================================

echo "================================================================"
echo "Iniciando aprovisionamiento en Nube Privada OpenStack..."
echo "================================================================"

# 1. Crear la red virtual y subred interna (Neutron)
echo "[NEUTRON] Creando red virtual 'nube_privada_net'..."
openstack network create nube_privada_net

echo "[NEUTRON] Creando subred 'nube_privada_subnet' (172.20.0.0/24)..."
openstack subnet create \
  --network nube_privada_net \
  --subnet-range 172.20.0.0/24 \
  --gateway 172.20.0.1 \
  --dns-nameserver 8.8.8.8 \
  nube_privada_subnet

# 2. Configurar el Router virtual y puerta de enlace exterior (Neutron)
echo "[NEUTRON] Creando router virtual de la nube..."
openstack router create cloud_router

echo "[NEUTRON] Configurando gateway exterior..."
openstack router set --external-gateway public cloud_router

echo "[NEUTRON] Conectando la subred al router..."
openstack router add subnet cloud_router nube_privada_subnet

# 3. Crear el Grupo de Seguridad para Nginx y el tráfico interno (Neutron)
echo "[NEUTRON] Configurando Grupos de Seguridad (Firewalls)..."
openstack security group create sec_group_web --description "Tránsito HTTP/HTTPS público"
openstack security group rule create --protocol tcp --dst-port 80:80 --remote-ip 0.0.0.0/0 sec_group_web
openstack security group rule create --protocol tcp --dst-port 443:443 --remote-ip 0.0.0.0/0 sec_group_web

openstack security group create sec_group_internal --description "Comunicación interna privada"
openstack security group rule create --protocol tcp --dst-port 1:65535 --remote-ip 172.20.0.0/24 sec_group_internal
openstack security group rule create --protocol icmp sec_group_internal

# 4. Aprovisionar almacenamiento persistente (Cinder)
echo "[CINDER] Creando volúmenes persistentes para bases de datos (10 GB)..."
openstack volume create --size 10 --description "Postgres Primary Volume" postgres_primary_volume
openstack volume create --size 10 --description "Postgres Standby Volume" postgres_standby_volume

# 5. Reservar puertos con IPs estáticas fijas en Neutron
echo "[NEUTRON] Creando puertos virtuales con IPs fijas..."
openstack port create --network nube_privada_net --fixed-ip subnet=nube_privada_subnet,ip-address=172.20.0.10 --security-group sec_group_web --security-group sec_group_internal nginx_port
openstack port create --network nube_privada_net --fixed-ip subnet=nube_privada_subnet,ip-address=172.20.0.20 --security-group sec_group_internal tomcat_port
openstack port create --network nube_privada_net --fixed-ip subnet=nube_privada_subnet,ip-address=172.20.0.30 --security-group sec_group_internal mosquitto_port
openstack port create --network nube_privada_net --fixed-ip subnet=nube_privada_subnet,ip-address=172.20.0.40 --security-group sec_group_internal postgres_primary_port
openstack port create --network nube_privada_net --fixed-ip subnet=nube_privada_subnet,ip-address=172.20.0.50 --security-group sec_group_internal postgres_standby_port
openstack port create --network nube_privada_net --fixed-ip subnet=nube_privada_subnet,ip-address=172.20.0.60 --security-group sec_group_internal telemetry_port

# 6. Levantar las instancias de servidores en Nova usando las IPs reservadas
echo "[NOVA] Aprovisionando instancias de servidores (VMs)..."
IMAGE_SO="Ubuntu-22.04-LTS"
FLAVOR_MIN="m1.tiny"
FLAVOR_MED="m1.small"
KEY_SSH="garaje-key"

openstack server create --image $IMAGE_SO --flavor $FLAVOR_MIN --key-name $KEY_SSH --port nginx_port nginx-edge-router
openstack server create --image $IMAGE_SO --flavor $FLAVOR_MED --key-name $KEY_SSH --port tomcat_port tomcat-app-server
openstack server create --image $IMAGE_SO --flavor $FLAVOR_MIN --key-name $KEY_SSH --port mosquitto_port mosquitto-mqtt-broker
openstack server create --image $IMAGE_SO --flavor $FLAVOR_MED --key-name $KEY_SSH --port postgres_primary_port postgres-primary-db
openstack server create --image $IMAGE_SO --flavor $FLAVOR_MED --key-name $KEY_SSH --port postgres_standby_port postgres-standby-db
openstack server create --image $IMAGE_SO --flavor $FLAVOR_MIN --key-name $KEY_SSH --port telemetry_port telemetry-microservice

# 7. Asociar volúmenes Cinder a las máquinas virtuales Nova
echo "[NOVA / CINDER] Asociando volúmenes a las instancias correspondientes..."
openstack server add volume postgres-primary-db postgres_primary_volume --device /dev/vdb
openstack server add volume postgres-standby-db postgres_standby_volume --device /dev/vdb

echo "================================================================"
echo "¡Infraestructura OpenStack aprovisionada exitosamente!"
echo "================================================================"
