# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# gateway-service — imagem de produção (multi-stage)
#   1) build: compila com Maven (JDK 21) — as dependências ficam em cache de camada
#   2) runtime: só o JRE 21 + o .jar, rodando com usuário sem privilégios
# Os testes NÃO rodam aqui: rodam no pipeline de CI antes do build da imagem.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1) só o pom: baixa as dependências (camada reaproveitada enquanto o pom não mudar)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 2) código-fonte e empacotamento
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S finora && adduser -S finora -G finora
COPY --from=build /workspace/target/*.jar /app/app.jar
USER finora

EXPOSE 8080

# JVM ciente dos limites do container (requests/limits do Kubernetes)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
