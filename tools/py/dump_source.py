"""Collects every source file of the ported project into one Markdown document."""
import os

ROOT = "src/mbga-master"
OUT = "dist/MBGA-libxposed102-source.md"

SECTIONS = [
    (
        "1. Gradle / 项目配置",
        [
            "settings.gradle.kts",
            "gradle.properties",
            "app/build.gradle.kts",
        ],
    ),
    (
        "2. libxposed 打包元数据与清单",
        [
            "app/src/main/AndroidManifest.xml",
            "app/src/main/resources/META-INF/xposed/module.prop",
            "app/src/main/resources/META-INF/xposed/java_init.list",
            "app/src/main/resources/META-INF/xposed/scope.list",
        ],
    ),
    (
        "3. 模块入口（libxposed API 102）",
        [
            "app/src/main/java/top/trangle/mbga/hook/HookEntry.kt",
        ],
    ),
    (
        "4. Hook 支撑层（替代 YukiHookAPI 的 DSL，直接落在 libxposed 拦截器链上）",
        [
            "app/src/main/java/top/trangle/mbga/xposed/HookEnv.kt",
            "app/src/main/java/top/trangle/mbga/xposed/ModulePrefs.kt",
            "app/src/main/java/top/trangle/mbga/xposed/EmptyPreferences.kt",
            "app/src/main/java/top/trangle/mbga/xposed/Finders.kt",
            "app/src/main/java/top/trangle/mbga/xposed/Hooks.kt",
            "app/src/main/java/top/trangle/mbga/xposed/YLog.kt",
        ],
    ),
    (
        "5. 配置通道（模块 App → 框架 remote preferences）",
        [
            "app/src/main/java/top/trangle/mbga/prefs/RemotePrefsBridge.kt",
            "app/src/main/java/top/trangle/mbga/application/DefaultApplication.kt",
            "app/src/main/java/top/trangle/mbga/views/SettingsActivity.kt",
        ],
    ),
    (
        "6. Hooker 基类与 8 个 Hooker（业务逻辑保持上游原样，仅换调用方式）",
        [
            "app/src/main/java/top/trangle/mbga/utils/MyHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/MainActivityHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/VideoPlayerHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/VideoDetailHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/VideoCommentHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/HomeViewHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/SearchViewHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/MineViewHooker.kt",
            "app/src/main/java/top/trangle/mbga/hook/SettingEntryHooker.kt",
        ],
    ),
    (
        "7. 未改动的上游文件（仅为完整性列出）",
        [
            "app/src/main/java/top/trangle/mbga/Constants.kt",
            "app/src/main/java/top/trangle/mbga/utils/Reflect.kt",
            "app/src/main/java/top/trangle/mbga/utils/factory/FunctionFactory.kt",
            "app/src/main/java/top/trangle/mbga/views/IntEditTextPreference.kt",
            "app/src/main/java/top/trangle/mbga/views/KeywordListPreference.kt",
        ],
    ),
]

LANG = {
    ".kt": "kotlin",
    ".kts": "kotlin",
    ".xml": "xml",
    ".properties": "properties",
    ".list": "text",
    ".prop": "properties",
}


def main():
    lines = [
        "# MBGA · libxposed API 102 改造后完整源码",
        "",
        "来源仓库：https://github.com/cledwynl/mbga （master，v1.5.0）",
        "本文件由脚本生成，包含改造后工程中所有被新增或修改的源文件；文件路径均为",
        "`src/mbga-master/` 下的相对路径。",
        "",
        "- 构建：`powershell -NoProfile -ExecutionPolicy Bypass -File .\\build.ps1`",
        "- 产物：`dist/MBGA-v1.5.0-libxposed102-vc170.apk`（versionCode 170）",
        "- 已删除的上游文件：`app/src/main/assets/xposed_init`、`app/src/main/resources/META-INF/yukihookapi_init`",
        "",
    ]
    total = 0
    missing = []
    for title, files in SECTIONS:
        lines.append(f"## {title}")
        lines.append("")
        for rel in files:
            path = os.path.join(ROOT, rel.replace("/", os.sep))
            if not os.path.exists(path):
                missing.append(rel)
                continue
            with open(path, encoding="utf-8") as f:
                content = f.read().rstrip("\n")
            total += content.count("\n") + 1
            lang = LANG.get(os.path.splitext(rel)[1], "text")
            lines.append(f"### `{rel}`")
            lines.append("")
            lines.append(f"```{lang}")
            lines.append(content)
            lines.append("```")
            lines.append("")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"files written: {OUT}")
    print(f"source lines: {total}")
    if missing:
        print("missing:", missing)


if __name__ == "__main__":
    main()
