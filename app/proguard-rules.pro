# ================================================================
# AO3 Lector - ProGuard / R8 rules
# Revisadas con android-proguard-helper (scan + check + templates).
# Release: isMinifyEnabled + isShrinkResources (R8 full mode, AGP 9.2.1).
# ================================================================

# --- Stack traces legibles en release ---------------------------
# El default proguard-android-optimize.txt NO mantiene SourceFile ni
# LineNumberTable (solo AnnotationDefault/EnclosingMethod/InnerClasses/
# RuntimeVisible*/Signature): sin estas lineas, un crash en las builds
# firmadas sale sin numero de linea, lo que dificulta depurar aun
# teniendo el mapping.
-keepattributes SourceFile,LineNumberTable

# --- jsoup ------------------------------------------------------
# Parser HTML del catalogo, capitulos, comentarios y perfiles.
# Defensivo (jsoup ya trae consumer rules) pero garantiza que R8
# nunca toque el parser, que es el corazon de la app.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# --- Puente WebView <-> JS del lector ---------------------------
# toggleChrome / onJsFindResult se inyectan con @JavascriptInterface.
# Si R8 ofusca o elimina esos metodos, el buscador del lector y el
# tap para ocultar la UI dejan de funcionar SOLO en release
# (debug funciona, por eso es tan traicionero).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# --- Componentes del manifest -----------------------------------
# R8 los conserva por estar registrados en el manifest; se dejan
# explicitos como red de seguridad (los marco el scan de la skill).
# Sin { *; }: R8 puede seguir ofuscando/eliminando miembros privados.
-keep class net.spin.ao3.MainActivity
-keep class net.spin.ao3.data.DownloadQueueService

# --- Librerias cubiertas por sus consumer rules (no tocar) ------
# - OkHttp 5.4.0  -> META-INF/proguard/okhttp3.pro (pooling, TLS, okio)
# - kotlinx.coroutines -> META-INF/proguard/coroutines.pro
# - Compose (BOM) -> consumer rules de cada artefacto
# - org.json -> parte del framework de Android, no requiere reglas
# No hay Gson/Retrofit/Reflection/Parcelable/Serializable en el codigo
# (verificado: solo org.json en Store.kt, sin Class.forName ni ::class.java).
