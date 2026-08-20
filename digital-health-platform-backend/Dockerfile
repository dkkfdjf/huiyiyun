# syntax=docker/dockerfile:1
# 慧医云后端镜像:多阶段构建
#   build  阶段用 JDK 22(Lombok 必须在 22 上编译;25-EA 会挂),maven 走 aliyun 镜像(复刻本机 settings.xml)
#   runtime 阶段用 JRE 21(跑 target 17 字节码;21 是 LTS),Debian jammy 自带 wget 供 healthcheck
#
# 构建上下文 = backend/ 目录(pom.xml + settings.xml + src)。
# 机密(application-local.yml)被 .dockerignore 挡掉,绝不进镜像;容器靠环境变量注入。

# ===== Stage 1: build =====
FROM maven:3.9-eclipse-temurin-22 AS build
WORKDIR /build
# 先拷 pom + settings.xml,单独跑 dependency:go-offline 做缓存层:改代码时这层命中缓存,不必重下依赖
COPY pom.xml settings.xml ./
RUN mvn -s settings.xml -B dependency:go-offline
# 再拷源码打包(跳过测试;CI 另跑测试)
COPY src ./src
RUN mvn -s settings.xml -B package -DskipTests

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# 非 root 运行,收敛容器内权限
RUN groupadd -r app && useradd -r -g app app
COPY --from=build /build/target/huiyiyun-platform.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
# 健康检查:actuator health 端点;start-period 给 Flyway 迁移留足冷启动时间
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --spider -q http://localhost:8080/actuator/health || exit 1
