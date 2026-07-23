# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package \
    && cp target/car-sharing-service-*.jar target/application.jar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /application

RUN addgroup --system app \
    && adduser --system --ingroup app app

COPY --from=builder --chown=app:app /workspace/target/application.jar application.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
