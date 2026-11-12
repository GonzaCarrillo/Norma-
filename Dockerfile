FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends python3-venv libwebp7 && python3 -m venv /opt/voice && /opt/voice/bin/pip install --no-cache-dir edge-tts==7.3.1 && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/voice/bin:${PATH}" NORMA_DB=/data/norma.db NORMA_UPLOADS=/data/uploads
WORKDIR /app
RUN useradd -r -u 10001 norma && mkdir /data && chown norma /data
COPY --from=build /app/target/taller-1.0.0.jar app.jar
USER norma
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java","--enable-native-access=ALL-UNNAMED","-XX:+UseCompactObjectHeaders","-Xmx384m","-jar","app.jar"]
