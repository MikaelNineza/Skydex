# Build from the repo root so :shared is available:
#   docker build -f server/Dockerfile -t skydex-server .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
ENV SKYDEX_SKIP_APP=true
RUN ./gradlew :server:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/libs/*-all.jar server.jar
EXPOSE 8080
CMD ["java", "-jar", "server.jar"]
