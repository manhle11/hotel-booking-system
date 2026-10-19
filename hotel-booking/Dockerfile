# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY hotel-booking/pom.xml hotel-booking/pom.xml
COPY hotel-booking/src hotel-booking/src
COPY docs/knowledge-base docs/knowledge-base

WORKDIR /workspace/hotel-booking
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system hanbay \
    && useradd --system --gid hanbay --home-dir /app --shell /usr/sbin/nologin hanbay

WORKDIR /app
COPY --from=build /workspace/hotel-booking/target/server-0.0.1-SNAPSHOT.jar app.jar

USER hanbay
EXPOSE 8080

ENV SPRING_DOCKER_COMPOSE_ENABLED=false
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
