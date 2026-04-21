# ── EJa Chess — Game Service ──────────────────────────────────────────────────
# Pre-build the Quarkus fast-jar before running docker-compose:
#   ./gradlew :modules:chess-api:quarkusBuild
#
# Or use:  make docker-up   (builds + starts everything)
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jre-alpine
WORKDIR /deployments

RUN addgroup -S chess && adduser -S chess -G chess

COPY --chown=chess:chess build/quarkus-app/lib/     lib/
COPY --chown=chess:chess build/quarkus-app/*.jar    ./
COPY --chown=chess:chess build/quarkus-app/app/     app/
COPY --chown=chess:chess build/quarkus-app/quarkus/ quarkus/

EXPOSE 8080
USER chess

CMD ["java", "-jar", "quarkus-run.jar"]
