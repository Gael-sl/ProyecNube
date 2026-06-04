package com.garage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garage.config.HibernateUtil;
import com.garage.model.Telemetry;
import com.garage.util.CryptoUtil;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.hibernate.Session;
import org.hibernate.Transaction;

@WebListener
public class MqttTelemetrySubscriber implements ServletContextListener {

    private static final String BROKER_URL = "tcp://mosquitto:1883";
    private static final String CLIENT_ID = "TomcatTelemetryReceiver";
    private static final String TOPIC = "cars/telemetry/encrypted";
    
    private MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Thread connectionThread;
    private volatile boolean running = true;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("Inicializando el Listener Suscriptor MQTT...");
        
        connectionThread = new Thread(() -> {
            while (running) {
                try {
                    System.out.println("Intentando conectar al Broker Mosquitto en: " + BROKER_URL);
                    mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setCleanSession(true);
                    options.setAutomaticReconnect(true);
                    options.setConnectionTimeout(10);
                    
                    mqttClient.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            System.err.println("¡Conexión MQTT perdida! Paho intentará reconexión automática: " + cause.getMessage());
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) throws Exception {
                            String encryptedPayload = new String(message.getPayload());
                            try {
                                // 1. Descifrar mediante AES-128
                                String decryptedJson = CryptoUtil.decryptAES(encryptedPayload);
                                
                                // 2. Deserializar el JSON usando Jackson
                                Telemetry telemetry = objectMapper.readValue(decryptedJson, Telemetry.class);
                                
                                // 3. Guardar en la base de datos usando Hibernate
                                saveTelemetryToDB(telemetry);
                                
                            } catch (Exception e) {
                                System.err.println("Error al descifrar/procesar la telemetría recibida: " + e.getMessage());
                            }
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                            // Solo suscripción, no publicación
                        }
                    });

                    mqttClient.connect(options);
                    mqttClient.subscribe(TOPIC);
                    System.out.println("Suscrito con éxito al canal MQTT: " + TOPIC);
                    break; // Conectado y suscrito, salir del ciclo
                    
                } catch (Exception e) {
                    System.err.println("Fallo al conectar el suscriptor MQTT: " + e.getMessage() + ". Reintentando en 5 segundos...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        connectionThread.start();
    }

    private void saveTelemetryToDB(Telemetry telemetry) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.persist(telemetry);
            transaction.commit();
            System.out.println(String.format("Telemetría descifrada persistida: %s %s - %s km/h (Base de Datos Activa)", 
                    telemetry.getBrand(), telemetry.getModel(), telemetry.getSpeed()));
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            System.err.println("Escritura en BD fallida (podría ser por failover activo de Postgres): " + e.getMessage());
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("Destruyendo el Listener Suscriptor MQTT...");
        running = false;
        if (connectionThread != null) {
            connectionThread.interrupt();
        }
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("Suscriptor MQTT desconectado.");
            }
        } catch (MqttException e) {
            System.err.println("Error al desconectar MQTT: " + e.getMessage());
        }
        
        // Apagar Hibernate Session Factory
        HibernateUtil.shutdown();
        System.out.println("Contexto de Hibernate cerrado.");
    }
}
