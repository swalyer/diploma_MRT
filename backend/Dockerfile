FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package \
 && test -f target/mrt-backend-0.0.1-SNAPSHOT.jar \
 && jar xf target/mrt-backend-0.0.1-SNAPSHOT.jar META-INF/MANIFEST.MF \
 && grep -Eq '^Main-Class: org.springframework.boot.loader.launch.JarLauncher|^Main-Class: org.springframework.boot.loader.JarLauncher' META-INF/MANIFEST.MF \
 && rm -rf META-INF

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/mrt-backend-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
