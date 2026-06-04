package com.garage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garage.config.HibernateUtil;
import com.garage.model.Telemetry;
import com.garage.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/telemetry")
public class TelemetryServlet extends HttpServlet {
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean checkAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Falta token de autorización"));
            return false;
        }
        
        String username = JwtUtil.verifyToken(authHeader);
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Token JWT inválido o expirado"));
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!checkAuth(request, response)) {
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Obtener los últimos 50 registros de telemetría ordenados por ID de forma descendente
            List<Telemetry> logs = session.createQuery("from Telemetry order by id desc", Telemetry.class)
                    .setMaxResults(50)
                    .list();
            
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), logs);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Error al consultar telemetría: " + e.getMessage()));
        }
    }
}
