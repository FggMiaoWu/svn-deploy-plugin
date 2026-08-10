rootProject.name = "svn-deploy-plugin"

// 插件解析仓库（阿里云镜像，避免访问 plugins.gradle.org 失败）
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
    }
}

// 依赖解析仓库（阿里云镜像）
dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        mavenCentral()
    }
}
