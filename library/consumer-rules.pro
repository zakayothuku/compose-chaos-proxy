# Chaos Proxy classes are plain Kotlin data classes/enums accessed only via direct method
# calls (no reflection, no Gson/Moshi/kotlinx-serialization). R8's default shrinker already
# keeps anything reachable from consumer code, so no keep rules are required for the
# model/ and engine/ packages.
#
# The repository/ package exposes the library's public programmatic API (documented in the
# README), so its public surface is preserved to keep it usable via reflection-based tooling
# (e.g. Espresso/UI test frameworks) without pinning down internal/private members.
-keep class io.github.zakayothuku.chaosproxy.repository.ChaosConfigRepository { public *; }
-keep public class io.github.zakayothuku.chaosproxy.repository.ChaosConfigState { public *; }
-keep public enum io.github.zakayothuku.chaosproxy.repository.ChaosPresetType { *; }
