FROM maven:3-openjdk-17

ENV APP_DIR /app
ENV TMP_DIR /tmp

WORKDIR $TMP_DIR

COPY pom.xml pom.xml

RUN mvn install

COPY src src

RUN mvn install -o && \
    mkdir $APP_DIR && \
    mv target/nanopub-http-*-SNAPSHOT-fat.jar $APP_DIR/nanopub-http.jar && \
    rm -rf $TMP_DIR

WORKDIR $APP_DIR

EXPOSE 9300
EXPOSE 9393

ENTRYPOINT ["java","-jar","nanopub-http.jar"]
