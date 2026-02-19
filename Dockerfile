FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd --system --uid 1001 spring
COPY --from=builder /app/build/libs/*.jar /app/app.jar
RUN chown -R spring:spring /app

ENV TZ=Asia/Seoul
ENV JAVA_OPTS=""

EXPOSE 8080
USER spring
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
