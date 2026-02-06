# 修复 GitHub Actions 构建错误

## 错误原因

```
Build was configured to prefer settings repositories over project repositories 
but repository 'Google' was added by build file 'build.gradle'
```

`settings.gradle` 设置了 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`，禁止在项目级 build.gradle 中添加仓库，但 `build.gradle` 中又声明了仓库。

## 解决方案

将 `settings.gradle` 中的模式从 `FAIL_ON_PROJECT_REPOS` 改为 `PREFER_SETTINGS`：

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)  // 改为 PREFER_SETTINGS
    repositories {
        google()
        mavenCentral()
        maven { url 'https://api.xposed.info/' }
    }
}
```

## 重新推送

```bash
cd ForceStatusBar
git add settings.gradle
git commit -m "Fix: 修改 repositories 模式为 PREFER_SETTINGS"
git push origin main
```

然后等待 GitHub Actions 重新构建。
