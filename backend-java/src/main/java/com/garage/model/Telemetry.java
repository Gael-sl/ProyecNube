package com.garage.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "telemetry_logs")
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private Double speed;

    @Column(nullable = false)
    private Integer rpm;

    @Column(name = "engine_temp", nullable = false)
    @JsonProperty("engine_temp")
    private Double engineTemp;

    @Column(nullable = false)
    private Integer gear;

    @Column(name = "reading_timestamp", nullable = false)
    private Long timestamp;

    // Constructors
    public Telemetry() {}

    public Telemetry(String brand, String model, Double speed, Integer rpm, Double engineTemp, Integer gear, Long timestamp) {
        this.brand = brand;
        this.model = model;
        this.speed = speed;
        this.rpm = rpm;
        this.engineTemp = engineTemp;
        this.gear = gear;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public Integer getRpm() { return rpm; }
    public void setRpm(Integer rpm) { this.rpm = rpm; }

    public Double getEngineTemp() { return engineTemp; }
    public void setEngineTemp(Double engineTemp) { this.engineTemp = engineTemp; }

    public Integer getGear() { return gear; }
    public void setGear(Integer gear) { this.gear = gear; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
