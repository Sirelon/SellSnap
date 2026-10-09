FROM eclipse-temurin:21-jre
COPY build/install/server /app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["/app/bin/server"]
