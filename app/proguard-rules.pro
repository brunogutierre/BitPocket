# Release builds shrink and obfuscate with R8. The BDK bindings call into Rust through JNA, which
# finds classes, fields and callbacks by reflection and from native code: R8 cannot see those uses,
# so both packages are kept whole. Neither AAR ships consumer rules (checked for BDK 3.1.0 and
# JNA 5.14.0).

# JNA: structures, callbacks and the native dispatch are reached by name.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**

# BDK UniFFI bindings: generated FFI structures and callback interfaces.
-keep class org.bitcoindevkit.** { *; }
