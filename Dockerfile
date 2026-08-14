# ============================================================
# Stage 1: Build Jar
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY .mvn ./.mvn
COPY mvnw mvnw.cmd ./
COPY pom.xml .
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ============================================================
# Stage 2: Lean Production Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S finsight && adduser -S finsight -G finsight
USER finsight:finsight

COPY --from=builder /app/target/finsight-ai-0.1.0-M1.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
