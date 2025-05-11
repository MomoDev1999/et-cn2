#Dockerfile
FROM eclipse-temurin:22-jdk AS buildstage 

RUN apt-get update && apt-get install -y maven

#directorio de trabajo
WORKDIR /app

COPY pom.xml .
COPY src /app/src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:22-jdk 

# Crear directorio para el wallet
RUN mkdir -p /opt/oracle/wallet

# Copiar el jar
COPY --from=buildstage /app/target/Backend-0.0.1-SNAPSHOT.jar /app/backend.jar

# Copiar el wallet a la nueva ubicación y establecer permisos
COPY Wallet_QMX8X1I4LTY0NIR1/* /opt/oracle/wallet/
RUN chmod -R 600 /opt/oracle/wallet

#Puerto
EXPOSE 8085

CMD [ "java", "-jar", "/app/backend.jar" ]


