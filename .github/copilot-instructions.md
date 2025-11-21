# Repo-specific Copilot instructions for stillpage

- Big picture
  - Android-first monorepo. Main Android app lives in `app/` (Gradle + Kotlin, minSdk 24, Java 17 toolchain). Two important modules in `modules/`: `book` (epub/UMD utilities) and `rhino` (JS execution helpers). A lightweight web admin UI lives in `modules/web/` (Vite + Vue 3 + TypeScript).
  - Data flow: rules (book source, replace rules, toc rules) are parsed and executed in the app (see `app/src/main/java/io/stillpage/app/rules` and `app/src/main/assets` for examples). The `modules/book` library handles ebook formats (EPUB/UMD) used by the app.

- How to build & test (developer workflows)
  - Android (from project root): use Gradle wrappers; common commands:
    - `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`) to build debug APK
    - `gradlew.bat assembleRelease -PRELEASE_STORE_FILE=path -P...` for signed builds
    - `gradlew.bat clean` to clean
  - Web UI (from `modules/web/`): requires Node >=20 and pnpm >=9 as declared in `modules/web/package.json`.
    - `pnpm install` then `pnpm run dev` to start Vite dev server
    - `pnpm run build` to build production site (script runs type-check + build + sync)

- Project-specific conventions & patterns
  - Versioning: `app/version.properties` is auto-incremented by `app/build.gradle` — builds change `VERSION_CODE` and compute `versionName` from it. Avoid manually editing `version.properties` unless intentional.
  - Native libs: `app/cronetlib/` contains prebuilt Cronet jars/aar; network stack uses Cronet + OkHttp. See `app/build.gradle` and `app/download.gradle` for Cronet management.
  - Rule format: Book-source rules and replace rules are JSON/text stored as assets or user data. See sample rules under `app/src/main/assets` and code that loads them in `app/src/main/java/io/stillpage/app/logic`.
  - Room schemas: exported under `app/schemas/` and wired via `ksp`/room compiler flags. If editing Room entities, run a clean build to update generated schemas.
  - UI strings & assets: `app/src/main/res/` and `app/src/main/assets/web/` include help pages used in-app; prefer editing the markdown files under `app/src/main/assets/web/help/md/` for help content.

- Integration points and external dependencies
  - Google services / Firebase: configured via `app/google-services.json` and controlled by gradle properties; release builds may require signing configs set via Gradle properties (`RELEASE_STORE_FILE`, etc.).
  - Native Cronet: Cronet artifacts live in `app/cronetlib` and are referenced as implementation fileTree; special proguard rules in `app/cronet-proguard-rules.pro`.
  - Web service: app includes a tiny embedded webserver (NanoHTTPD) for local web UI; see usages in `app/src/main/java/io/stillpage/app/webserver`.

- Fast navigation & examples (explicit file references)
  - Entry points:
    - Android app main package: `app/src/main/java/io/stillpage/app/` (look at `MainActivity`, `App.kt`)
    - Rule parsing and execution: `app/src/main/java/io/stillpage/app/rules/` and `app/src/main/java/io/stillpage/app/logic/`
  - Modules:
    - `modules/book/` — EPUB/UMD utilities (Java)
    - `modules/rhino/` — Rhino JS host code used for rule evaluation
    - `modules/web/` — Vite + Vue admin UI; dev scripts in `package.json`

- Small gotchas for agents
  - Builds require Java 17 and Android SDK matching `compile_sdk_version` (35). Use Android Studio or ensure `ANDROID_HOME`/`ANDROID_SDK_ROOT` is set when building on CI.
  - `app/build.gradle` mutates `version.properties` during build — be careful when running repeated builds in CI; add `-PskipVersionIncrement=true` if you create such a property (not present by default).
  - Many dependencies are declared via `libs.versions.toml` (Gradle version catalog) under `gradle/libs.versions.toml`; resolve aliases if editing dependencies.
  - Do not modify generated code or schemas directly; change the source entity then rebuild.

- Example PR task for agents
  1. Add a small feature (e.g., new help markdown) under `app/src/main/assets/web/help/md/`.
  2. Run `gradlew.bat assembleDebug` locally to ensure no compile errors.
  3. Add tests if applicable and include a short run-note in PR description explaining why `version.properties` may change.


If any section is unclear or you want more examples (e.g., show code snippets for rule parsing, Room entity locations, or the web UI structure), tell me which area to expand and I will iterate.

## 仓库专用 Copilot 指南（中文）

- 总览
  - 以 Android 为主的 monorepo。主应用位于 `app/`（Gradle + Kotlin，minSdk 24，Java 17 toolchain）。重要模块：`modules/book`（EPUB/UMD 处理）和 `modules/rhino`（用于在 app 中执行 JS 规则）。轻量级 Web 管理界面位于 `modules/web/`（Vite + Vue 3 + TypeScript）。
  - 数据流：书源规则、替换规则、目录规则由应用解析并执行（参见 `app/src/main/java/io/stillpage/app/rules` 和 `app/src/main/assets`）。`modules/book` 处理电子书格式并被 `app` 引用。

- 构建与开发（常用命令）
  - Android（在项目根运行）：使用 Gradle wrapper
    - 构建调试包：`./gradlew assembleDebug`（Windows: `gradlew.bat assembleDebug`）
    - 签名发布：`gradlew.bat assembleRelease -PRELEASE_STORE_FILE=path -P...`（需设置签名相关 Gradle 属性）
    - 清理：`gradlew.bat clean`
  - Web（在 `modules/web/`）：需要 Node >= 20、pnpm >= 9（见 `modules/web/package.json`）
    - 安装依赖：`pnpm install`
    - 本地开发：`pnpm run dev`（启动 Vite）
    - 生产构建：`pnpm run build`（包含类型检查并执行 `scripts/sync.js`）

- 项目约定与模式（重要的、只能从仓库中发现的）
  - 版本号：`app/version.properties` 会在构建时由 `app/build.gradle` 自动递增（`VERSION_CODE`），并据此计算 `versionName`。CI/本地构建时会改动此文件，避免手动编辑（除非明确需要）。
  - 本地原生库：`app/cronetlib/` 存放预编译的 Cronet jars/aar，网络层使用 Cronet + OkHttp，相关处理见 `app/download.gradle` 与 `app/build.gradle`。
  - 规则格式：书源规则、替换规则等以 JSON/text 存放在 assets 或用户数据中。示例位置：`app/src/main/assets`，加载与执行逻辑在 `app/src/main/java/io/stillpage/app/logic`。
  - Room schema：导出到 `app/schemas/`，通过 KSP/Room 编译器参数生成。修改 Room 实体后需要做一次干净构建以更新 schema。
  - 帮助文档：应用内帮助页面以 markdown 形式放在 `app/src/main/assets/web/help/md/`，优先编辑这些 md 文件以更新应用内帮助内容。

- 集成点与外部依赖（注意行为与配置位置）
  - Firebase/Google 服务：配置文件为 `app/google-services.json`，发布签名由 Gradle 属性控制（如 `RELEASE_STORE_FILE` 等）。
  - Cronet（本地网络实现）：见 `app/cronetlib/` 与 `app/cronet-proguard-rules.pro`。
  - 嵌入式 Web 服务：仓库使用 NanoHTTPD 提供本地 Web UI 支持，查看实现 `app/src/main/java/io/stillpage/app/webserver`。

- 快速定位（示例文件）
  - App 入口：`app/src/main/java/io/stillpage/app/`（参考 `MainActivity`, `App.kt`）
  - 规则解析与执行：`app/src/main/java/io/stillpage/app/rules/`、`app/src/main/java/io/stillpage/app/logic/`
  - 模块：`modules/book/`（EPUB/UMD 工具）、`modules/rhino/`（JS 运行宿主）、`modules/web/`（Vite + Vue 前端）

- 常见坑（Agents 特别应注意）
  - 构建需要 Java 17 与 Android SDK（compileSdkVersion = 35）。在 CI 或本地构建前确保 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 指向可用的 SDK。
  - `app/build.gradle` 会在构建期间修改 `version.properties`。在自动化或重复构建场景中，这会造成变动；如需避免可考虑传入自定义属性（仓库中未默认实现 `-PskipVersionIncrement`）。
  - 依赖通过 Gradle 版本目录 `gradle/libs.versions.toml` 声明（仓库使用版本别名），编辑依赖时请参考该文件以解析 alias。
  - 不要直接编辑生成代码或 schema，而是修改源并重新构建以避免不一致。

- 示例 PR 步骤（对 AI agent 有明确可执行的操作）
  1. 增加一个简单功能（例如新增帮助 md）：把文件放到 `app/src/main/assets/web/help/md/`。
  2. 本地验证：`gradlew.bat assembleDebug`（确保能编译通过）。
  3. 在 PR 描述中说明 `version.properties` 可能变化的原因。

- 进一步阅读位置
  - `README.md` / `English.md`（仓库总览）
  - `app/build.gradle`, `download.gradle`, `gradle/libs.versions.toml`（依赖与构建细节）
  - `modules/web/package.json`（Web 前端的运行/构建脚本与环境要求）

如果你希望我把某一节扩展为更详细的“开发快速上手”或添加具体代码片段（例如：书源规则 JSON 实例、Room 实体示例、`modules/rhino` 的调用示例），请告诉我想要扩展的部分，我会基于仓库里的真实文件补充示例并再提交一次更新。