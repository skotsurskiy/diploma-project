# syntax=docker/dockerfile:1
# Контекст збірки — корінь монорепозиторію (див. infra/docker-compose.yml).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN chmod +x mvnw && ./mvnw -B -q -pl payment-service -am dependency:go-offline || true
COPY payment-service/src payment-service/src
RUN ./mvnw -B -pl payment-service -am clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/payment-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
