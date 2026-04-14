# Kotlin-Way Refactoring Design

## Summary

Пошаговый рефакторинг проекта `ci-workflows` для улучшения читаемости, единообразия и соответствия Kotlin-конвенциям. Проект генерирует GitHub Actions YAML-файлы через Kotlin DSL (`github-workflows-kt` + собственный `workflow-dsl` модуль).

## Фазы

### Фаза 1: Gradle Version Catalog

**Цель:** Устранить дублирование версий зависимостей между модулями.

**Что делаем:**
- Создаём `gradle/libs.versions.toml` с секциями `[versions]`, `[libraries]`, `[plugins]`
- Версии: `github-workflows-kt = "3.7.0"`, `kaml = "0.104.0"`, `kotlinx-serialization-core = "1.11.0"`, `kotlin = "2.3.20"`
- Обновляем `build.gradle.kts` (root) и `workflow-dsl/build.gradle.kts` на ссылки из каталога

**Файлы:**
- Создать: `gradle/libs.versions.toml`
- Изменить: `build.gradle.kts`, `workflow-dsl/build.gradle.kts`

---

### Фаза 2: Разбиение `Workflows.kt` на пакет `workflows/definitions/`

**Цель:** Один top-level объект = один файл (Kotlin-конвенция). Улучшение навигации.

**Что делаем:**
- Удаляем `src/main/kotlin/workflows/Workflows.kt`
- Создаём 7 файлов в пакете `workflows.definitions`:
  - `CheckWorkflow.kt`
  - `ConventionalCommitCheckWorkflow.kt`
  - `CreateTagWorkflow.kt`
  - `ManualCreateTagWorkflow.kt`
  - `ReleaseWorkflow.kt`
  - `PublishWorkflow.kt`
  - `LabelerWorkflow.kt`
- Обновляем все импорты в base-workflows, adapters и `Generate.kt`

**Файлы:**
- Удалить: `src/main/kotlin/workflows/Workflows.kt`
- Создать: 7 файлов в `src/main/kotlin/workflows/definitions/`
- Изменить: все файлы в `workflows/base/`, `workflows/adapters/`, `WorkflowHelpers.kt`

---

### Фаза 3: `AppDeployWorkflow` объект

**Цель:** Привести `AppDeploy` к общему паттерну — все base-workflows имеют соответствующий workflow-объект.

**Что делаем:**
- Создаём `workflows/definitions/AppDeployWorkflow.kt` — `object : ProjectWorkflow("app-deploy.yml")` с inputs (`setup-action`, `setup-params`, `deploy-command`, `tag`) и `JobBuilder`
- Обновляем `workflows/base/AppDeploy.kt` — `generateAppDeploy()` использует `AppDeployWorkflow.inputs` вместо inline map-а
- Убираем параметр `outputDir: File` из `generateAppDeploy()` (другие `generate*()` его не принимают)

**Файлы:**
- Создать: `src/main/kotlin/workflows/definitions/AppDeployWorkflow.kt`
- Изменить: `src/main/kotlin/workflows/base/AppDeploy.kt`, `src/main/kotlin/generate/Generate.kt`

---

### Фаза 4: Обобщение tag-адаптеров

**Цель:** DRY — устранить дублирование между Gradle/Go вариантами tag-адаптеров.

**Что делаем:**
- Создаём `CreateTagAdapter` — `class : ProjectAdapterWorkflow` с параметрами `(fileName, workflowName, tool: SetupTool, defaultVersion, defaultCommand, defaultTagPrefix)`
- Создаём `ManualCreateTagAdapter` — аналогичный класс
- Удаляем 4 файла: `GradleCreateTag.kt`, `GoCreateTag.kt`, `GradleManualCreateTag.kt`, `GoManualCreateTag.kt`
- В `Generate.kt` создаём экземпляры с нужными параметрами

**Файлы:**
- Удалить: 4 файла в `workflows/adapters/tag/`
- Создать: `CreateTagAdapter.kt`, `ManualCreateTagAdapter.kt` в `workflows/adapters/tag/`
- Изменить: `Generate.kt`

---

### Фаза 5: Переименование делегатов

**Цель:** Более выразительные имена, отражающие тип возвращаемого значения.

**Что делаем:**
- `InputProperty` → `StringInputProperty`, `inputProp()` → `stringInput()`
- `InputRefProperty` → `RefInputProperty`, `inputRefProp()` → `refInput()`
- Обновляем все использования в `JobBuilder`-ах

**Файлы:**
- Изменить: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`
- Изменить: все файлы в `workflows/definitions/` (после фазы 2)

---

### Фаза 6: Исправление header-комментария

**Цель:** Header в генерируемых YAML-файлах должен отражать актуальное расположение исходников.

**Что делаем:**
- В `AdapterWorkflow.kt` обновляем header:
  - Было: `This file was generated using Kotlin DSL (.github/workflow-src/$slug.main.kts).`
  - Стало: `This file was generated using Kotlin DSL (src/main/kotlin/).`
- Проверяем и обновляем аналогичный header в base-workflows (параметр `sourceFile` в `workflow()`)

**Файлы:**
- Изменить: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`
- Возможно: base-workflow файлы (параметр `sourceFile`)

---

## Порядок выполнения

Фазы 1-6 выполняются последовательно. Проект должен компилироваться после каждой фазы. Каждая фаза — отдельный коммит.

## Что НЕ входит в рефакторинг

- Изменение функциональности (генерируемые YAML-файлы должны оставаться идентичными)
- Рефакторинг YAML-сериализации (`AdapterWorkflowYaml.kt`)
- Изменение структуры `workflow-dsl` модуля (кроме делегатов в фазе 5)
- Добавление тестов (отдельная задача)
