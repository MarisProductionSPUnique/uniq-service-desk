FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B package -DskipTests
FROM tailscale/tailscale:v1.104.7 AS tailscale
FROM eclipse-temurin:17-jre
COPY --from=tailscale /usr/local/bin/tailscale /usr/local/bin/tailscale
COPY --from=tailscale /usr/local/bin/tailscaled /usr/local/bin/tailscaled
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/banking-1.0.0.jar app.jar
EXPOSE 8080
COPY start.sh /app/start.sh
ENTRYPOINT ["/bin/sh","/app/start.sh"]
