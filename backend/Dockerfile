# 측정·배포용 이미지. 먼저 ./gradlew bootJar 로 jar 를 만든다.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/backend-0.0.1-SNAPSHOT.jar app.jar
# 컨테이너 메모리 제한에 맞춰 힙을 잡는다 (기본 25% 는 1.6GB 제한에서 너무 작다).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar \"$@\"", "--"]
