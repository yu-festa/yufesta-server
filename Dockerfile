FROM gradle:9.3.0-jdk17 AS builder
WORKDIR /app

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
# MaxRAMPercentage: 컨테이너 메모리(태스크 1GB)의 70%를 힙 상한으로. 기본 25%면 Spring Boot에 부족하다
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
