FROM azul/zulu-openjdk:8u362 as builder

ADD https://dlcdn.apache.org/maven/maven-3/3.9.1/binaries/apache-maven-3.9.1-bin.tar.gz /opt/

RUN tar xzf /opt/apache-maven-3.9.1-bin.tar.gz -C /opt/ && \
    rm -f /opt/apache-maven-3.9.1-bin.tar.gz

ENV MAVEN_HOME=/opt/apache-maven-3.9.1; \
    PATH=$PATH:$MAVEN_HOME/bin

ENV APP_HOME=/opt/keycloak-authenticator-wmp

WORKDIR $APP_HOME

ADD . .

RUN /opt/apache-maven-3.9.1/bin/mvn -U clean package -DskipTests -Dmaven.test.skip

FROM quay.io/keycloak/keycloak:21.0.2

COPY --from=builder /opt/keycloak-authenticator-wmp/target/keycloak-authenticator-wmp-1.0-SNAPSHOT.jar /opt/keycloak/providers/keycloak-authenticator-wmp-1.0-SNAPSHOT.jar
