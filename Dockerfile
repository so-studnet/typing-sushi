FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/src ./src
RUN mkdir -p out && javac -d out $(find src/main/java -name '*.java')

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./backend/out
COPY backend/wordbank ./backend/wordbank
COPY frontend ./frontend

WORKDIR /app/backend
EXPOSE 8080
CMD ["java", "-cp", "out", "com.typingsushi.Main"]
