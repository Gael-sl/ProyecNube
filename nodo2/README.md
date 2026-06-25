# NODO 2 - Guía de Configuración (Laptop del Amigo)

Esta carpeta contiene todo lo necesario para ejecutar el **Nodo Secundario** en la segunda laptop durante la presentación.

## Requisitos
- **Docker Desktop** instalado y ejecutándose en esta laptop.

---

## Instrucciones de Lanzamiento Paso a Paso

### 1. Conexión de Red (Punto a Punto)
Ambas laptops deben estar conectadas a la misma red:
- Pueden usar la misma red de la universidad.
- O compartir datos desde un teléfono celular (Hotspot) y conectar ambas laptops a esa red.

### 2. Obtener las Direcciones IP
- **Laptop 1 (La Nube):** Abre la terminal y escribe `ipconfig`. Busca la dirección IPv4 (ej. `192.168.1.15`).
- **Laptop 2 (Tú):** Abre la terminal y escribe `ipconfig`. Busca tu dirección IPv4 (ej. `192.168.1.20`).

### 3. Configurar el Entorno en la Laptop 2
En esta carpeta `nodo2`, crea un archivo llamado `.env` o edita el sistema para pasarle la IP de la Laptop 1.
La forma más fácil es crear un archivo llamado `.env` en esta carpeta con el siguiente contenido:

```env
NUBE_IP=192.168.1.15
```
*(Reemplaza `192.168.1.15` con la IP real de la Laptop 1).*

### 4. Lanzar los Servicios
Abre una terminal en esta carpeta (`nodo2`) y ejecuta:
```bash
docker compose up --build -d
```
Esto iniciará:
1. **La Base de Datos Standby:** Se conectará automáticamente a la base de datos de la Laptop 1 en el puerto `5473` y empezará a replicarla en tiempo real.
2. **El Simulador de Telemetría (Python):** Enviará datos cifrados por AES al broker MQTT en la Laptop 1.

---

## ¿Cómo Probar el Failover Físico?
1. Con ambos sistemas corriendo, abre la página web en la Laptop 1 (`http://localhost:8085/app`). Verás que la BD activa es la `.40` (Laptop 1) y los carros se mueven.
2. Apaga el primario en la Laptop 1: `docker compose stop postgres-primary`.
3. Observa la página web. En unos 10-15 segundos, la base de datos en **TU LAPTOP** (Laptop 2) se auto-promoverá a Primaria.
4. El Tomcat de la Laptop 1 se conectará automáticamente a tu IP (Laptop 2) en el puerto `5432`. El indicador web cambiará a ONLINE y mostrará tu IP (`NODO2_IP`), demostrando el failover a través de dos computadoras físicas.
