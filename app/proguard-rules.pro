# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# Biometric
-keep class androidx.biometric.** { *; }

# Keep data classes used in JSON serialization (notes)
-keep class com.privateai.camera.security.SecureNote { *; }

# Keep bridge classes (ONNX detector)
-keep class com.privateai.camera.bridge.** { *; }

# SQLCipher
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Crypto
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# PdfBox-Android references an optional JPEG 2000 decoder (com.gemalto.jp2)
# that we don't ship — Privora-rendered PDFs are JPEG/PNG, not JP2.
# Without this rule R8 fails the release build on the missing reference.
-dontwarn com.gemalto.jp2.**

# LiteRT-LM ships an unused JCommander helper in some samples — silence
# R8 warnings about it so the release build stays clean.
-dontwarn com.beust.jcommander.**
-dontwarn com.google.ai.edge.litertlm.**
