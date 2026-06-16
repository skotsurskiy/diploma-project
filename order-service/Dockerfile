# syntax=docker/dockerfile:1
# Контекст збірки — корінь монорепозиторію (див. infra/docker-compose.yml).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Спершу копіюємо тільки pom-файли — кешуємо завантаження залежностей.
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
RUN chmod +x mvnw && ./mvnw -B -q -pl order-service -am dependency:go-offline || true
# Тепер вихідний код і збірка тільки потрібного модуля.
COPY order-service/src order-service/src
RUN ./mvnw -B -pl order-service -am clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
