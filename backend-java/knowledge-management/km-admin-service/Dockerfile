FROM maven:3.8.6-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY backend-java backend-java
WORKDIR /workspace/backend-java
RUN mvn -pl knowledge-management/km-admin-service -am package -DskipTests

FROM eclipse-temurin:8-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/backend-java/knowledge-management/km-admin-service/target/km-admin-service-1.0.0.jar app.jar
EXPOSE 9101
ENTRYPOINT ["java","-jar","/app/app.jar"]
