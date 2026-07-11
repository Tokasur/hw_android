# SDL's native code calls into these classes via JNI by name.
-keep class org.libsdl.app.** { *; }

# The Pascal engine exports JNI functions bound to this class.
-keep class org.hedgewars.hedgeroid.EngineProtocol.PascalExports { *; }

# Keep native method names everywhere.
-keepclasseswithmembernames class * {
    native <methods>;
}
