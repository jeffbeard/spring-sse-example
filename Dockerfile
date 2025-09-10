# Multi-stage Docker build for Spring Boot SSE application
# Stage 1: Build the application
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copy gradle wrapper and gradle files first (for better caching)
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code and build
COPY src/ src/
RUN ./gradlew build -x test --no-daemon

# Stage 2: Runtime image
FROM openjdk:17-jre-slim

WORKDIR /app

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Change ownership to spring user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring

# Expose port 8080
EXPOSE 8080

# Set JVM options for containerized environments
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]