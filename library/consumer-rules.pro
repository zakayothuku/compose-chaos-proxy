# Preserve Chaos Proxy model & engine classes during R8 obfuscation
-keep class io.github.zakayothuku.chaosproxy.model.** { *; }
-keepclassmembers class io.github.zakayothuku.chaosproxy.model.** { *; }
-keep class io.github.zakayothuku.chaosproxy.engine.** { *; }
-keepclassmembers class io.github.zakayothuku.chaosproxy.engine.** { *; }
-keep class io.github.zakayothuku.chaosproxy.repository.** { *; }
-keepclassmembers class io.github.zakayothuku.chaosproxy.repository.** { *; }
