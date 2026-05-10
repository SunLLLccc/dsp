FROM openjdk:8-jre-slim
ARG JAR_FILE
ARG PORT=8080
WORKDIR /app
COPY ${JAR_FILE} app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
