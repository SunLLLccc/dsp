FROM debian:bookworm-slim
ARG JAR_FILE
ARG PORT=8080
COPY jdk /opt/jdk
ENV JAVA_HOME=/opt/jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
WORKDIR /app
COPY ${JAR_FILE} app.jar
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
