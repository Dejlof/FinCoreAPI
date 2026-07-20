# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S fincore && adduser -S fincore -G fincore
WORKDIR /app
COPY --from=build /app/target/fincore-api-1.0.0.jar app.jar
USER fincore
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
