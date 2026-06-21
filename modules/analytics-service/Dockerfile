# ── EJa Chess — Analytics Service ─────────────────────────────────────────────
# Pre-build the Quarkus fast-jar before running docker-compose:
#   ./gradlew :modules:analytics-service:quarkusBuild
#
# Then start with docker-compose (all services):
#   docker-compose up --build
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jre
WORKDIR /deployments

RUN groupadd -r chess && useradd -r -g chess chess

COPY --chown=chess:chess build/quarkus-app/lib/     lib/
COPY --chown=chess:chess build/quarkus-app/*.jar    ./
COPY --chown=chess:chess build/quarkus-app/app/     app/
COPY --chown=chess:chess build/quarkus-app/quarkus/ quarkus/

EXPOSE 8082
USER chess

ENV JAVA_OPTS=""

CMD ["sh", "-c", "java $JAVA_OPTS -jar quarkus-run.jar"]
