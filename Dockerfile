FROM eclipse-temurin:23-alpine

RUN apk add --no-cache curl
RUN apk add --no-cache bash
RUN apk add --no-cache maven
RUN apk add --no-cache git

ENV APP_DIR /app
ENV TMP_DIR /tmp

WORKDIR $TMP_DIR

COPY pom.xml pom.xml

RUN mvn install

COPY src src

RUN mvn install -o && \
    mkdir $APP_DIR && \
    mv target/nanopub-http-*-fat.jar $APP_DIR/nanopub-http.jar && \
    rm -rf $TMP_DIR

WORKDIR $APP_DIR

EXPOSE 4800

ENTRYPOINT ["java","-jar","nanopub-http.jar"]
