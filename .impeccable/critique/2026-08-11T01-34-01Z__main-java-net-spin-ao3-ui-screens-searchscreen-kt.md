---
target: apartado de busqueda (SearchScreen)
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
timestamp: 2026-08-11T01-34-01Z
slug: main-java-net-spin-ao3-ui-screens-searchscreen-kt
---
# Critique — Apartado de búsqueda (SearchScreen + WorkCard + FilterSheet)

## Design Health Score: 28/40 (Bueno)

| # | Heurística | Score | Hallazgo clave |
|---|-----------|-------|----------------|
| 1 | Visibilidad del estado | 3 | Spinners bien; falta conteo de resultados ("1.234 obras") |
| 2 | Coincidencia mundo real | 3 | Español natural, jerga AO3 correcta; "Fandom / tag a explorar" algo técnico |
| 3 | Control y libertad | 3 | Back/limpiar/"Limpiar filtros" ok; falta "Limpiar todo" en el sheet |
| 4 | Consistencia | 3 | Campo inline sin borde rompe con OutlinedTextField del resto |
| 5 | Prevención de errores | 2 | Campos libres (palabras/fechas) sin validación |
| 6 | Reconocimiento | 3 | Sugerencias de tags excelentes; SwapVert ambiguo, badge sin exp. TalkBack |
| 7 | Flexibilidad | 3 | 10 órdenes; sin atajos para re-ejecutar búsqueda |
| 8 | Estético y minimalista | 2 | FilterSheet = muro de ~10 secciones sin disclosure |
| 9 | Recuperación de errores | 3 | Banner "filtros no aplicados", retry, empty state con acción |
| 10 | Ayuda y docs | 3 | Hints inline; sin ayuda para novatos de AO3 |

## Especificidad de diseño
Contenido muy AO3 (facetas con conteos, Incluir/Excluir, chips de fandom/kudos). Chrome genérico aceptable. Riesgo de intercambiabilidad: iconos sin etiqueta y campo que parece texto estático.

## Carga cognitiva
3 fallos de 8: chunking del sheet, opciones mínimas, disclosure progresivo. Concentrada en el FilterSheet.

## Problemas priorizados
- [P1] FilterSheet: muro de ~10 secciones sin colapsar. Fix: acordeón / "Común" vs "Avanzado".
- [P1] Campo de búsqueda del top bar sin affordance (parece título, no campo). Fix: píldora con fondo surfaceContainerLow + borde sutil.
- [P2] Iconos sin etiqueta + badge no expuesto a TalkBack. Fix: semantics fusionada; label de orden visible.
- [P2] Sin "Limpiar todo" en el FilterSheet.
- [P2] Sin conteo de resultados.
- [P3] Validación de campos libres.

## Fortalezas
1. Sugerencias de tags con conteos (reconocimiento puro, muy AO3).
2. Top bar compacto.
3. Estados de recuperación honestos.
