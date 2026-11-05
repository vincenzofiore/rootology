# The deployable image (#12): the production jar (./mvnw -Pproduction package) on a Java 25 runtime,
# split into Spring Boot's layers so an update usually ships only the application layer.
FROM eclipse-temurin:25.0.5_8-jre-resolute AS layers
WORKDIR /build
COPY target/rootology-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25.0.5_8-jre-resolute
LABEL org.opencontainers.image.source="https://github.com/vincenzofiore/rootology" \
      org.opencontainers.image.licenses="Apache-2.0"
RUN useradd --system --uid 10001 --no-create-home rootology
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
USER rootology
EXPOSE 8080
# Heap from the container's memory limit; a JVM out of memory stops, and Docker restarts it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
