# ============================================================================
#  Imagen de un componente Spring Boot de FoodFlow (HU-607)
# ============================================================================
#  Una sola receta para order-service, payment-service, notification-service y
#  api-gateway: Compose la usa con el directorio de cada componente como contexto,
#  así que ninguno depende del código de otro (regla 8).
#  Imagen base: docs/wiki/04-implementacion/versiones.md (Java 25, eclipse-temurin:25-jdk).
# ============================================================================
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Las pruebas se ejecutan fuera de la imagen (./mvnw verify); aquí solo se empaqueta.
RUN ./mvnw -B -q -DskipTests package && cp target/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:25-jdk
WORKDIR /app
RUN useradd --system --no-create-home foodflow
COPY --from=build /app/app.jar app.jar
USER foodflow
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
