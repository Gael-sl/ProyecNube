package com.garage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garage.config.HibernateUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/health")
public class HealthServlet extends HttpServlet {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> health = new HashMap<>();

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            
            // Ejecutar consulta nativa para obtener la IP activa de conexión de PostgreSQL
            String activeDbIp = session.createNativeQuery(
                    "SELECT CAST(inet_server_addr() AS VARCHAR)", String.class)
                    .getSingleResult();

            // Ejecutar consulta nativa para comprobar si la BD está en modo de recuperación (standby) o lectura-escritura (primary)
            Boolean isInRecovery = session.createNativeQuery(
                    "SELECT pg_is_in_recovery()", Boolean.class)
                    .getSingleResult();

            if (activeDbIp == null || activeDbIp.isEmpty()) {
                activeDbIp = "localhost";
            }

            String nodeRole = Boolean.TRUE.equals(isInRecovery) ? "Standby" : "Primary";

            health.put("status", "UP");
            health.put("database_active_node_ip", activeDbIp + " (" + nodeRole + ")");
            response.setStatus(HttpServletResponse.SC_OK);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("database_active_node_ip", "DESCONECTADO");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        objectMapper.writeValue(response.getWriter(), health);
    }
}
