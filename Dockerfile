FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Use the HTTPS Google Cloud Maven Central mirror to avoid upstream throttling.
RUN printf '%s\n' '<settings><mirrors><mirror><id>google-maven-central</id><url>https://maven-central.storage-download.googleapis.com/maven2/</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' > /tmp/maven-settings.xml
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    for attempt in 1 2 3; do \
      if mvn -s /tmp/maven-settings.xml -B -U -C package -DskipTests; then exit 0; fi; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      sleep "$((attempt * 30))"; \
    done
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
