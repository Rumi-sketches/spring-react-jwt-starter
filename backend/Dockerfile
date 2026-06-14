# ── Stage 1: Build ───────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia il wrapper Maven e il pom.xml PRIMA del codice sorgente.
# Docker costruisce a layer: se pom.xml non cambia, le dipendenze
# restano in cache e non vengono riscaricate ad ogni build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Corregge i line ending Windows (CRLF → LF) — necessario su macchine Windows
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Scarica le dipendenze in un layer separato
RUN ./mvnw dependency:go-offline -B

# Ora copia il codice e compila
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────────
# Immagine finale molto più piccola: JRE invece di JDK completo
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]