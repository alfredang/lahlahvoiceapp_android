# ML Kit GenAI (Gemini Nano) ships its own consumer rules. Keep its public API for safety.
-keep class com.google.mlkit.genai.** { *; }
-dontwarn com.google.mlkit.genai.**
