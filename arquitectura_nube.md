# Arquitectura del Sistema en Nube Privada (OpenStack)

Este documento detalla el diseño de arquitectura y el plan de despliegue en la nube para la práctica final **Garaje Deportivo**. Explica cómo se estructuran las capas del sistema y cómo se realiza el mapeo de componentes desde el entorno de pruebas local (Docker) hacia una nube privada real basada en **OpenStack**.

---

## 1. Tríada de OpenStack: Mapeo de Arquitectura

Para garantizar la estabilidad y tolerancia a fallos exigida por el proyecto, implementamos la infraestructura utilizando los tres pilares de una nube privada: **Cómputo (Nova)**, **Redes (Neutron)** y **Almacenamiento (Cinder)**.

A continuación se muestra el mapeo exacto de cómo se traduce nuestro entorno simulado de contenedores locales a recursos reales en OpenStack:

| Componente del Proyecto | Simulación Local (Docker) | Despliegue Cloud (OpenStack) | Función Técnica |
| :--- | :--- | :--- | :--- |
| **Edge Router (Nginx)** | Contenedor `nginx` | **Nova VM** `nginx-edge-router` | Servidor proxy de borde. Recibe tráfico público HTTPS/TLS, gestiona certificados y enruta al backend. |
| **Servidor App (Tomcat)** | Contenedor `tomcat` | **Nova VM** `tomcat-app-server` | Backend de la aplicación. Ejecuta la lógica Java (Servlets, Hibernate, Cliente MQTT). |
| **Broker de Mensajería** | Contenedor `mosquitto` | **Nova VM** `mosquitto-mqtt-broker` | Cola de mensajería distribuida ligera (MQTT) para recibir datos telemétricos. |
| **Base de Datos Activa** | Contenedor `postgres-primary` | **Nova VM** `postgres-primary-db` | Base de datos relacional PostgreSQL activa (Master) que recibe escrituras. |
| **Base de Datos Réplica** | Contenedor `postgres-standby` | **Nova VM** `postgres-standby-db` | Base de datos secundaria (Standby) que sincroniza datos y realiza failover automático. |
| **Microservicio** | Contenedor `microservice-telemetry` | **Nova VM** `telemetry-microservice` | Simulador autónomo de telemetría de vehículos que cifra en AES-128. |
| **Red Distribuidora** | Red Docker `nube_privada_net` | **Neutron Private Network** y **Subnet** | Red privada virtualizada que conecta de forma aislada todos los servidores en la subred `172.20.0.0/24`. |
| **Almacenamiento Persistente** | Volúmenes locales de Docker | **Cinder Block Volumes** | Volúmenes de disco duro de 10GB montados en `/dev/vdb` para asegurar la permanencia de la base de datos. |

---

## 2. Configuración de Red en OpenStack Neutron

**Neutron** se encarga de crear el entorno de red aislado y asegurar los puertos. 

### Direccionamiento IP Estático en la Nube
Para evitar fallas de conexión debido a cambios en las IPs internas por DHCP, asignamos puertos virtuales de Neutron con direcciones IP estáticas fijas en la subred de la nube:
* **Nginx (Edge Router / SSL)**: `172.20.0.10` (Puerto público y privado)
* **Tomcat (Servicios API)**: `172.20.0.20`
* **Mosquitto (MQTT Broker)**: `172.20.0.30`
* **PostgreSQL Primario**: `172.20.0.40`
* **PostgreSQL Standby (Réplica)**: `172.20.0.50`
* **Microservicio de Telemetría**: `172.20.0.60`

### Reglas de Firewalls (Security Groups)
Configuramos reglas específicas a nivel de puerto para restringir el acceso:
1. **Grupo de Seguridad Web (`sec_group_web`)**:
   - Asignado a `nginx-edge-router`.
   - Permite la entrada de tráfico HTTP (puerto 80) e HTTPS (puerto 443) desde cualquier origen público (`0.0.0.0/0`) para que los clientes del navegador puedan acceder al dashboard.
2. **Grupo de Seguridad Interno (`sec_group_internal`)**:
   - Asignado a todos los nodos de la nube.
   - Permite la comunicación irrestricta de todos los puertos TCP/UDP e ICMP (Ping) **únicamente** si el origen proviene de la subred privada de la nube (`172.20.0.0/24`). 
   - Bloquea cualquier intento de conexión directa desde el exterior hacia las bases de datos (puerto 5432), broker (puerto 1883) o Tomcat (puerto 8080).

---

## 3. Almacenamiento Persistente de Bloques (Cinder)

Las máquinas virtuales creadas en Nova son efímeras (si se destruyen, se pierde su almacenamiento local). Para evitar que los datos de inventario y telemetría de los carros deportivos se pierdan, provisionamos discos en **Cinder**:
- `postgres_primary_volume` (10 GB) -> Asociado a la instancia `postgres-primary-db` montado en el punto `/dev/vdb`.
- `postgres_standby_volume` (10 GB) -> Asociado a la instancia `postgres-standby-db` montado en el punto `/dev/vdb`.

Las carpetas de datos de PostgreSQL (`/var/lib/postgresql/data`) se configuran en el sistema operativo para escribir directamente sobre estos discos de Cinder.

---

## 4. Tolerancia a Fallos y Replicación Activo-Pasiva en la Nube

Para garantizar la alta disponibilidad del sistema de base de datos distribuidas en la nube, implementamos un esquema de replicación física por streaming y failover automatizado:

```
[Cliente API Java (Conexión Multi-host)]
               │
               ├── (Intenta escribir en Primary) ──> [PostgreSQL Primary (172.20.0.40)] ── (Streaming) ──┐
               │                                                  │ (Cae la Instancia / VM)             │
               │                                                  ▼                                     ▼
               └── (Detecta caída y reconecta) ──> [PostgreSQL Standby (172.20.0.50)] <─────── [Promueve a Primary]
                                                         [Script Monitor de Fallos]
```

1. **Sincronización en Tiempo Real**: Al iniciar el servidor Standby (`172.20.0.50`), ejecuta `pg_basebackup` para clonar la base de datos del Primario (`172.20.0.40`) y entra en modo de recuperación continua (Hot Standby), replicando cada cambio en tiempo real.
2. **Monitoreo y Detección de Caídas**: La instancia Standby ejecuta un script monitor en segundo plano que vigila el estado de salud de la instancia Primaria.
3. **Promoción Automática (Failover)**: Si el servidor Primario falla (caída de VM o pérdida de red por más de 10 segundos), el script monitor del Standby ejecuta el comando:
   `pg_ctl promote -D /var/lib/postgresql/data`
   Esto elimina el estado de recuperación del standby y lo convierte instantáneamente en el nuevo nodo maestro de base de datos con permisos de lectura y escritura.
4. **Reconexión Transparente del Cliente**: El backend en Tomcat utiliza una cadena de conexión JDBC multi-host configurada de la siguiente forma:
   `jdbc:postgresql://172.20.0.40:5432,172.20.0.50:5432/car_db?targetServerType=primary`
   El controlador JDBC intentará conectarse primero al primario (`172.20.0.40`). Al fallar, se conectará al standby (`172.20.0.50`). Dado que el standby ya se habrá promovido a primario (dejando de ser de solo lectura), el controlador de base de datos lo validará como apto y el servidor continuará operando normalmente de forma transparente.

---

## 5. Instrucciones para Desplegar en OpenStack Real

Para implementar esta misma arquitectura en un panel real de OpenStack, tienes dos opciones automatizadas incluidas en el proyecto:

### Opción A: Despliegue con Heat (HOT) - Recomendado
1. Accede al dashboard de tu nube privada OpenStack (Horizon).
2. Ve a la sección **Project** -> **Orchestration** -> **Stacks**.
3. Haz clic en **Launch Stack**.
4. Sube el archivo `openstack-heat-template.yaml` proporcionado en la raíz del proyecto.
5. Define los parámetros (nombre de imagen de SO, tipo de sabor de VMs, y tu clave SSH).
6. Haz clic en **Launch**. OpenStack creará automáticamente el router, la red Neutron, puertos fijos, volúmenes de Cinder, VMs de Nova y montará los discos en menos de 3 minutos.

### Opción B: Despliegue por Consola (CLI)
1. Conéctate a un servidor con el cliente OpenStack CLI configurado.
2. Carga tus credenciales de proyecto (ej. `source openrc.sh`).
3. Ejecuta el script de aprovisionamiento:
   ```bash
   chmod +x openstack_setup.sh
   ./openstack_setup.sh
   ```
4. El script creará paso a paso la red de Neutron y las instancias en Nova.
