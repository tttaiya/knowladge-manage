FROM maven:3.8.6-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY backend-java backend-java
WORKDIR /workspace/backend-java
RUN --mount=type=cache,target=/root/.m2 mvn -pl knowledge-management/km-report-service -am package -DskipTests -Dmaven.wagon.http.retryHandler.count=5

FROM eclipse-temurin:8-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend-java/knowledge-management/km-report-service/target/km-report-service-1.0.0.jar app.jar
EXPOSE 9104
ENTRYPOINT ["java","-jar","/app/app.jar"]
