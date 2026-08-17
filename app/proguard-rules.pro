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

# --- Puente WebView <-> JS del lector ----------------------------
# toggleChrome / onJsFindResult / onScrollRatio se inyectan con
# @JavascriptInterface. VERIFICADO en el dex del release firmado: la regla
# generica `-keepclassmembers class * { @android.webkit.JavascriptInterface
# <methods>; }` NO surte efecto con R8 full mode de AGP 9 (grep JavascriptInterface
# en classes*.dex = 0) — el WebView no expone ningun metodo al JS y el puente
# falla SILENCIOSAMENTE solo en release (debug funciona, por eso es tan
# traicionero). Se conservan explicitamente los objetos anonimos anotados.
-keepclasseswithmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# WebViewFetcher ya NO usa @JavascriptInterface: el HTML se transfiere por
# console.log -> WebChromeClient.onConsoleMessage (canal nativo, inmune a R8).

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
