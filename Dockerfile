FROM eclipse-temurin:17-jre
WORKDIR /data
EXPOSE 25565
CMD ["java", "-Xms1G", "-Xmx2G", "-jar", "server.jar", "nogui"]