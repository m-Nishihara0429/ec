# --- ビルドステージ: Maven Wrapperでjarをビルドする ---
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# 依存関係の解決だけをまず走らせてDockerのレイヤーキャッシュを効かせるため、
# pom.xml関連ファイルだけ先にコピーする（ソースコードだけ変更した再ビルドを速くするため）
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# --- 実行ステージ: 軽量なJRE上でビルド済みjarだけを実行する ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/ec-site.jar app.jar

# Renderは環境変数PORTでリッスンポートを指定してくる（application.propertiesのserver.port=${PORT:8080}が対応）
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
