# Автоматическая сборка через GitHub Actions

В репозитории уже есть workflow `.github/workflows/build.yml`.

1. Загрузи все файлы проекта в GitHub-репозиторий.
2. Открой вкладку **Actions**.
3. Выбери **Build Wheat Sweep** и нажми **Run workflow**, либо просто отправь commit в репозиторий.
4. После успешной сборки открой завершённый запуск и скачай artifact `wheat-sweep-fabric-1.21.11`.
5. Внутри будет `wheat-sweep-1.2.0.jar`.

Workflow использует Java 21 и Gradle 9.2.1. Он загружает собранный JAR как artifact, но не публикует его в Releases.
