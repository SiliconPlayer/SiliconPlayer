# Native code resolves bridge methods by exact name; neither shrink nor
# obfuscate may touch them.
-keep class com.flopster101.siliconplayer.NativeBridge { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

# Optional transitive deps that are never loaded at runtime on desktop.
-dontwarn javax.annotation.**
-dontwarn javax.el.**
-dontwarn javax.activation.**
-dontwarn kotlinx.serialization.**

# SMBJ drags in two skewed BouncyCastle flavors; the dupes never load.
-dontwarn org.bouncycastle.**
