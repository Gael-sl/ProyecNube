# Stage 1: Build the WAR file using Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven Project Model Descriptor
COPY pom.xml .

# Download dependencies offline to speed up compilation
RUN mvn dependency:go-offline -B

# Copy codebase
COPY src ./src

# Compile and package application
RUN mvn package -DskipTests

# Stage 2: Deploy into Tomcat 10
FROM tomcat:10.1-jdk21

# Clean default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy WAR to Root context so it serves from /
COPY --from=builder /build/target/garage-api.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
