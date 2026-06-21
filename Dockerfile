# Étape d'exécution (ultra légère)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# On copie le .jar généré par l'étape de build de GitHub Actions
COPY target/*.jar app.jar
# Configuration de la mémoire pour le plan gratuit de Render (512Mo)
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xss512k -Xms256m -Xmx380m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]