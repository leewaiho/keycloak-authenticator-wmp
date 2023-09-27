FROM azul/zulu-openjdk:8u362 as builder
ARG MVN_VERSION=3.9.4

ADD https://dlcdn.apache.org/maven/maven-3/$MVN_VERSION/binaries/apache-maven-$MVN_VERSION-bin.tar.gz /opt/

RUN tar xzf /opt/apache-maven-$MVN_VERSION-bin.tar.gz -C /opt/ && \
    rm -f /opt/apache-maven-$MVN_VERSION-bin.tar.gz

ENV MAVEN_HOME=/opt/apache-maven-$MVN_VERSION; \
    PATH=$PATH:$MAVEN_HOME/bin

ENV APP_HOME=/opt/keycloak-authenticator-wmp

WORKDIR $APP_HOME

ADD . .

RUN /opt/apache-maven-$MVN_VERSION/bin/mvn -U clean package -DskipTests -Dmaven.test.skip

FROM quay.io/keycloak/keycloak:21.0.2

COPY --from=builder /opt/keycloak-authenticator-wmp/target/keycloak-authenticator-wmp-1.0-SNAPSHOT.jar /opt/keycloak/providers/keycloak-authenticator-wmp-1.0-SNAPSHOT.jar
