FROM azul/zulu-openjdk-alpine:8
VOLUME /tmp
COPY target/app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]