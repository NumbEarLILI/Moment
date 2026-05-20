# Moment

Moment 是一个安卓日常碎片记录 App。首版 MVP 支持记录生活片段，并基于当天碎片生成可编辑、可保存的日记手帐。

## 首版功能

- 记录文字碎片、心情、标签和图片 URI。
- 首页按当天时间线展示碎片。
- 删除不需要的碎片。
- 基于当天碎片生成本地规则日记草稿。
- 编辑并保存日记标题和正文。
- 查看历史日记列表与详情。
- 使用 Room 将碎片和日记保存在本地设备。

## 技术栈

- Kotlin
- Jetpack Compose
- Navigation Compose
- Room
- Hilt
- Coroutines / Flow
- JUnit

## 构建与测试

本仓库使用 Gradle Wrapper：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

如果本机没有 Android SDK，可运行：

```bash
bash scripts/setup-android-sdk.sh
```

脚本会安装 Command-line Tools、接受许可，并安装 `platform-tools`、`platforms;android-36`、`build-tools;36.0.0`（默认目录为项目下的 `.android-sdk/`）。

Gradle 会在未存在 `local.properties` 时，根据 `ANDROID_SDK_ROOT` / `ANDROID_HOME` 或 `.android-sdk/` 自动生成 `sdk.dir`。也可手动在 `local.properties` 中设置 `sdk.dir`。该文件不应提交到版本库。

Cursor Cloud Agent 会在启动时通过 `.cursor/environment.json` 执行上述安装脚本，详见 `AGENTS.md`。

## 文档

- 架构设计：`docs/superpowers/specs/2026-05-13-moment-architecture-design.md`
- 实现计划：`docs/superpowers/plans/2026-05-13-moment-mvp.md`
