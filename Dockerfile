# ---------- Stage 1: build (compila el JAR con Maven) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache de dependencias: copia primero el pom y descarga offline
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copia el código y empaqueta (sin tests para acelerar la imagen;
# los tests corren en CI/local, no en el build de la imagen)
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---------- Stage 2: runtime (solo JRE + el jar) ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Usuario no-root (buena práctica de seguridad para el panel)
RUN useradd -r -u 1001 appuser
USER appuser

# Copia el jar construido en el stage anterior
COPY --from=build /app/target/*.jar app.jar

# Virtual threads + arranque
ENV JAVA_OPTS="-XX:+UseZGC"
ENV SPRING_THREADS_VIRTUAL_ENABLED=true

# Puerto del actuator/health (ECS lo usa para el health check)
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
