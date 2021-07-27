FROM openjdk:11.0.12-oracle
WORKDIR /sibas
COPY . .
CMD chmod +x gradlew
ENTRYPOINT ["/bin/bash", "./gradlew", "run"]
