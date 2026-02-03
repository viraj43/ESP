# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/ESPAnalysis-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
CMD ["java", "-Xmx512m", "-jar", "app.jar"]
