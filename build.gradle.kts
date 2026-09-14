plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/central")
    mavenCentral()
}

// 运行时依赖：用 Apache POI 生成/填充上线申请表 docx。
// 注意：poi-ooxml 默认带 poi-ooxml-lite（精简版），不含 CT_P/CT_Tc 等底层 schema 类，
// 需改用 poi-ooxml-full 并排除 lite。
dependencies {
    implementation("org.apache.poi:poi-ooxml:5.2.5") {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation("org.apache.poi:poi-ooxml-full:5.2.5")
}

// 编译需要 JDK 17（IntelliJ 2023+ 要求）
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

intellij {
    // 使用本机已安装的 IDEA，避免下载 754MB IntelliJ SDK
    // macOS .app bundle 结构与插件预期不符（build.txt 在 Contents/Resources/ 下），
    // 故用 .idea-sdk 包装目录软链接所有组件，并补 build.txt 到根目录
    localPath.set(file(".idea-sdk").canonicalPath)
    plugins.set(listOf())
}

tasks {
    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("pluginSinceBuild"))
        untilBuild.set(providers.gradleProperty("pluginUntilBuild"))
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    // 启动沙箱 IDEA 调试插件
    runIde {
        // 可指定自定义 IDEA 安装路径，留空则用下载的沙箱
        // ideDir.set(file("/Applications/IntelliJ IDEA.app"))
    }

    buildSearchableOptions {
        enabled = false
    }
}
