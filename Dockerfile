FROM eclipse-temurin:17-jre

WORKDIR /data

# Copy everything into container
COPY . /data

EXPOSE 25565

CMD ["java", "-Xms4G", "-Xmx4G", "-jar", "server.jar", "nogui"]