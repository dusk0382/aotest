# Sistema de diseño — AO3 Lector

Spec vigente de la identidad visual. Los valores de aquí están copiados del
código: si cambian, se actualizan las dos cosas a la vez (esta spec y el
fichero Kotlin que se indica en cada sección).

- Tokens de color: `ui/theme/Theme.kt` (`LightColors`, `DarkColors`,
  `LightSemantic`, `DarkSemantic`)
- Acentos de categoría y escala de ratings: `ui/components/TagChip.kt`
- Tipografía y shapes: `ui/theme/Theme.kt` (`Ao3Typography`, `Ao3Shapes`)
- Temas del lector: `ui/reader/ReaderTheme.kt`

## 1. Paleta

**Material 3 baseline** — los valores canónicos de la especificación de
Material, no una paleta inventada. Sustituye a "Terracota & Salvia" (v0.7.26),
que tenía el primary terracota `#B03A2E` y el secondary cacao `#7C5825` con
ratio de contraste **1.06 entre sí**: marrón sobre marrón, tres acentos
apagados compitiendo sin separación cromática.

### Claro

| Token | Valor | Uso |
|---|---|---|
| `primary` | `#6750A4` | acciones, selección, foco, FAB |
| `onPrimary` | `#FFFFFF` | texto sobre `primary` (6.44:1) |
| `primaryContainer` | `#EADDFF` | chips activos, superficies destacadas |
| `onPrimaryContainer` | `#21005D` | texto dentro del container (13.32:1) |
| `secondary` | `#625B71` | acentos de apoyo, autor, fandom |
| `secondaryContainer` | `#E8DEF8` | rellenos secundarios |
| `tertiary` | `#7D5260` | tercer nivel de acento |
| `tertiaryContainer` | `#FFD8E4` | rellenos terciarios, advertencias |
| `surface` / `background` | `#FFFBFE` | lienzo de pantallas |
| `surfaceVariant` | `#E7E0EC` | metadatos, campos |
| `onSurface` | `#1C1B1F` | texto de cuerpo (16.71:1) |
| `onSurfaceVariant` | `#49454F` | texto secundario (7.24:1) |
| `surfaceContainerLowest` | `#FFFFFF` | zonas más altas |
| `surfaceContainerLow` | `#F7F2FA` | tarjetas |
| `surfaceContainer` | `#F3EDF7` | contenedores intermedios |
| `surfaceContainerHigh` | `#ECE6F0` | contenedores altos |
| `surfaceContainerHighest` | `#E6E0E9` | contenedores más altos |
| `outline` | `#79747E` | bordes |
| `outlineVariant` | `#CAC4D0` | divisores |
| `error` | `#B3261E` | errores |
| `errorContainer` | `#F9DEDC` | fondo de error |

### Oscuro

| Token | Valor | Contraste |
|---|---|---|
| `primary` | `#D0BCFF` | 7.71:1 |
| `onPrimary` | `#381E72` | — |
| `primaryContainer` | `#4F378B` | 7.23:1 |
| `secondary` | `#CCC2DC` | 7.74:1 |
| `secondaryContainer` | `#4A4458` | 7.19:1 |
| `tertiary` | `#EFB8C8` | 7.75:1 |
| `tertiaryContainer` | `#633B48` | 7.20:1 |
| `surface` / `background` | `#1C1B1F` | 13.27:1 |
| `surfaceVariant` | `#49454F` | 5.48:1 |
| `onSurface` | `#E6E1E5` | — |
| `surfaceContainerLowest` | `#131318` | |
| `surfaceContainerLow` | `#1D1B20` | |
| `surfaceContainer` | `#211F26` | |
| `surfaceContainerHigh` | `#2B2930` | |
| `surfaceContainerHighest` | `#36343B` | |
| `outline` | `#938F99` | |
| `outlineVariant` | `#49454F` | |
| `error` | `#F2B8B5` | 7.66:1 |

Las superficies nunca llegan a negro puro (`#000000`) ni a blanco puro en modo
claro, para evitar la halación del texto brillante sobre fondo puro durante
sesiones largas de lectura.

**Todos los pares `on-*` / `*-container` superan WCAG AA.** 27 de 28 alcanzan
AAA; el mínimo es 5.48:1 (`onSurfaceVariant` sobre `surfaceVariant` en oscuro).
Verificado con el linter de `@google/design.md` (30 pares, 0 findings de
`contrast-ratio`).

### Semánticos (`LocalSemanticColors`)

| Token | Claro | Oscuro |
|---|---|---|
| `success` | `#146C2E` | `#6DD58C` |
| `onSuccess` | `#FFFFFF` | `#00391B` |
| `favorite` | `#B3261E` | `#FFB4AB` |
| `warning` | `#8B5000` | `#FFB86B` |
| `info` | `#1565C0` | `#A8C7FA` |

`info` es un token de apoyo, **no** el acento de marca. Para lo que sea acción
principal, `primary`.

## 2. Reglas de superficie

**Superficies SÓLIDAS.** Prohibido `container.copy(alpha=…)` para fondos,
banners, chips o secciones. La opacidad queda reservada a **texto** y líneas
divisorias. Cuando se necesite una mezcla, usar un blend opaco
`lerp(surface, accent, k)`.

Motivo: un tinte translúcido sobre superficie clara reduce el contraste del
texto que va encima de forma impredecible según lo que haya debajo.

La variante TINTED de `TagChip` es un blend sólido `lerp(surface→accent, 18%)`,
no una transparencia.

## 3. Escala sólida de ratings y warnings

Contenedor + texto fijos, WCAG AA/AAA en ambos modos. Ratings y advertencias
son anclas de seguridad cognitiva: necesitan un color opaco e reconocible de un
vistazo, no un tinte. Implementación: `ratingSolidColors()` y
`warningSolidColors()` en `ui/components/TagChip.kt`.

| Clave | Claro (fondo/texto) | Ratio | Oscuro |
|---|---|---|---|
| General | `#C8E6FF` / `#00344A` | 10.20:1 | invertido |
| Teen | `#B9F0BB` / `#0A3D14` | 9.61:1 | invertido |
| Mature | `#FFDDB0` / `#4A2800` | 10.17:1 | invertido |
| Explicit | `#FFDAD6` / `#690005` | 10.14:1 | invertido |
| Not Rated | `#E3E1E1` / `#2B2B2B` | 10.87:1 | invertido |
| Warnings / default | `#FFD8E4` / `#4A1533` | 11.20:1 | invertido |

"En invertido" = el oscuro usa el texto claro como fondo y el container como
texto, lo que deja el mismo par en contraste alto en lugar de justo.

## 4. Acentos de categoría

Un color sólido por categoría de tag. Alimentan el punto, el borde o el texto
del chip, nunca un lavado translúcido.

| Categoría | Valor |
|---|---|
| Fandom | `#625B71` |
| Rating | `#8A5000` |
| Warning | `#B3261E` |
| Category | `#6750A4` |
| Relationship | `#A03462` |
| Character | `#146C2E` |
| Additional | `#6F6B73` |

## 5. Tipografía

Escala M3 estándar en `Ao3Typography`, sin tamaños literales en las pantallas.

| Rol | Tamaño / interlineado | Peso |
|---|---|---|
| `displaySmall` | 36 / 44 | SemiBold |
| `headlineMedium` | 28 / 36 | Bold |
| `headlineSmall` | 24 / 32 | Bold |
| `titleLarge` | 22 / 28 | SemiBold |
| `titleMedium` | 16 / 24 | SemiBold |
| `titleSmall` | 14 / 20 | SemiBold |
| `bodyLarge` | 16 / 24 | Normal |
| `bodyMedium` | 14 / 20 | Normal |
| `bodySmall` | 12 / 16 | Normal |
| `labelLarge` | 14 / 20 | Medium |
| `labelMedium` | 12 / 16 | Medium |
| `labelSmall` | 11 / 16 | Medium |

**Una sola familia en la interfaz:** Google Sans Flex (`res/font/gs_*.ttf`),
pesos 400/500/600/700. La fuente es variable (eje `wght` 1–1000); se
instanciaron los cuatro pesos estáticos para que funcione desde API 23 sin
`FontVariation` (que llega en 26). Licencia OFL, copia en
`app/src/main/assets/license_google_sans_flex.txt`.

**El lector no usa esta escala**: `ReaderTheme` y `ReaderPagination` siguen
eligiendo entre `FontFamily.Serif` y `SansSerif` según el ajuste del usuario
(Ajustes → Lector → Tipografía).

## 6. Shapes

| Token | Radio | Uso |
|---|---|---|
| `extraSmall` | 4 dp | |
| `small` | 8 dp | chips |
| `medium` | 12 dp | tarjetas, superficies |
| `large` | 16 dp | hojas grandes |
| `extraLarge` | 28 dp | diálogos, bottom sheets |

## 7. Colores dinámicos (Material You)

**Opt-in, default `false`.** `Ao3Theme(dynamicColor = false)` y `Store` lo
persiste en `false`. La identidad la fija esta spec, no el fondo de pantalla
del usuario.

Consecuencia práctica: si el usuario tiene Material You activado en el sistema,
la app ve su paleta y la spec **no se aplica**. Un bug de esto se reporta como
"la UI se ve rara" y la primera hipótesis es Material You, no un build viejo
(ver `docs/TESTING.md` §4).

## 8. Temas del lector

Independientes de la paleta de la app: el lector tiene 4 temas propios
(`Store.ReaderTheme`), porque el texto de la obra manda sobre la cromo de la
aplicación. `readerPalette()` devuelve `(fondo, texto, acento)`:

| Tema | Fondo | Texto | Acento |
|---|---|---|---|
| Light | `#ffffff` | `#1c1c1e` | `#2F6B35` |
| Sepia | `#f6edd9` | `#433422` | `#4F7A3A` |
| Dark | `#1b1b1f` | `#d6d3d0` | `#9AD8A6` |
| Black (AMOLED) | `#000000` | `#d4d4d4` | `#8FCF9E` |

El acento es verde de progreso de lectura en los 4, y cambia de tono con el
fondo para mantener contraste. Estos valores se inyectan también como CSS en
el WebView del modo scroll, así que los hex se mantienen como `String` a
propósito (ver el comentario de caché en `ReaderTheme.kt`).

## 9. Splash e icono

Fondo `#1C1B1F` (`res/values/colors.xml`: `splash_background` e
`ic_launcher_background`), el surface oscuro del tema. El icono del libro
ámbar se mantiene.