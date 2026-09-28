# Proveedor de notificaciones simulado (HU-306). Imagen: docs/wiki/04-implementacion/versiones.md
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Las pruebas se ejecutan fuera de la imagen (./mvnw verify); aquí solo se empaqueta.
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jdk
WORKDIR /app
RUN useradd --system --no-create-home mock
COPY --from=build /app/target/notification-provider-0.0.1-SNAPSHOT.jar app.jar
USER mock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
