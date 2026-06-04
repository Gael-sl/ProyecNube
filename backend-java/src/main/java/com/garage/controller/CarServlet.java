package com.garage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garage.config.HibernateUtil;
import com.garage.model.Car;
import com.garage.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/cars")
public class CarServlet extends HttpServlet {
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
            List<Car> cars = session.createQuery("from Car", Car.class).list();
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), cars);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Error al consultar la BD: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!checkAuth(request, response)) {
            return;
        }

        try {
            Car car = objectMapper.readValue(request.getInputStream(), Car.class);
            
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                Transaction transaction = session.beginTransaction();
                session.persist(car);
                transaction.commit();
                
                response.setStatus(HttpServletResponse.SC_CREATED);
                objectMapper.writeValue(response.getWriter(), car);
            } catch (Exception dbEx) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Error de base de datos: " + dbEx.getMessage()));
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "JSON de auto invalido"));
        }
    }
}
