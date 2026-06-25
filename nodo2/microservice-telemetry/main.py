import time
import random
import json
import base64
import os
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.backends import default_backend
import paho.mqtt.client as mqtt

# Configuración
MQTT_BROKER = os.getenv("MQTT_HOST", "mosquitto")
MQTT_PORT = 1883
MQTT_TOPIC = "cars/telemetry/encrypted"

# Clave secreta de 16 bytes para AES-128
AES_KEY = b"MySuperSecretKey"

CARS = [
    {"brand": "Porsche", "model": "911 GT3", "min_speed": 100, "max_speed": 318, "min_rpm": 3000, "max_rpm": 9000, "temp_base": 90},
    {"brand": "Ferrari", "model": "SF90 Stradale", "min_speed": 120, "max_speed": 340, "min_rpm": 4000, "max_rpm": 8500, "temp_base": 95},
    {"brand": "Lamborghini", "model": "Aventador SVJ", "min_speed": 110, "max_speed": 350, "min_rpm": 3500, "max_rpm": 8500, "temp_base": 98},
    {"brand": "Chevrolet", "model": "Corvette Z06", "min_speed": 80, "max_speed": 310, "min_rpm": 2500, "max_rpm": 8600, "temp_base": 88}
]

def encrypt_aes_128_cbc(plaintext: str, key: bytes) -> str:
    # Geneear Vector de Inicialización dinámico de 16 bytes
    iv = os.urandom(16)
    
    #Configurar el cifrador
    cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
    encryptor = cipher.encryptor()
    
    # Añade relleno PKCS7 para alinear el texto plano a bloques de 16 bytes
    padder = padding.PKCS7(128).padder()
    padded_data = padder.update(plaintext.encode('utf-8')) + padder.finalize()
    
    # Cifrar
    ciphertext = encryptor.update(padded_data) + encryptor.finalize()
    
    # Prefijar el IV al criptograma y luego codificar en base64
    combined = iv + ciphertext
    encoded = base64.b64encode(combined).decode('utf-8')
    return encoded

def on_connect(client, userdata, flags, rc):
    print(f"Conectado al Broker MQTT con codigo de resultado {rc}")

def main():
    print("Iniciando el microservicio de simulacion de telemetria.")
    
    # Configurar cliente MQTT
    client = mqtt.Client()
    client.on_connect = on_connect
    
    # Esperar a que Mosquitto esté listo
    connected = False
    while not connected:
        try:
            client.connect(MQTT_BROKER, MQTT_PORT, 60)
            connected = True
        except Exception as e:
            print("El broker MQTT no esta listo. Reintentando en 2 segundos.")
            time.sleep(2)
            
    client.loop_start()
    
    print("publicando eventos de telemetria.")
    
    while True:
        # Selecciona un auto al azar y genera valores
        car = random.choice(CARS)
        speed = round(random.uniform(car["min_speed"], car["max_speed"]), 1)
        rpm = random.randint(car["min_rpm"], car["max_rpm"])
        engine_temp = round(car["temp_base"] + random.uniform(0, 15), 1)
        gear = random.randint(3, 7)
        timestamp = int(time.time())
        
        # Construir la cadena JSON
        telemetry_data = {
            "brand": car["brand"],
            "model": car["model"],
            "speed": speed,
            "rpm": rpm,
            "engine_temp": engine_temp,
            "gear": gear,
            "timestamp": timestamp
        }
        
        raw_json = json.dumps(telemetry_data)
        
        # Cifrar mediante AES-128 CBC
        encrypted_payload = encrypt_aes_128_cbc(raw_json, AES_KEY)
        
        # Publicar en MQTT
        client.publish(MQTT_TOPIC, encrypted_payload)
        
        print("\n--- NUEVO EVENTO DE TELEMETRIA ---")
        print(f"JSON Original: {raw_json}")
        print(f"Carga cifrada (AES-128 Base64): {encrypted_payload[:60]}.")
        
        # Esperar 3 segundos antes de enviar la siguiente lectura
        time.sleep(3)

if __name__ == "__main__":
    main()
