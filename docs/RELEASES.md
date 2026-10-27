# Releases de ParDL

ParDL publica **dos líneas separadas** para no mezclar binarios ni notas:

| Plataforma | Etiqueta | Versión | Release | Assets |
|---|---|---|---|---|
| Windows | `pc-v0.4.0` | 0.4.0 | ParDL PC v0.4.0 — Portable | `ParDL-PC-0.4.0-portable.exe`, SHA-256 |
| Android | `v0.3.2` | 0.3.2 | ParDL Android v0.3.2 | APK arm64, APK universal |

## Reglas de organización

- Las releases de PC usan el prefijo **`pc-v`**.
- Las releases de Android usan el prefijo **`v`** (serie `v0.3.x`).
- La release de PC **no incluye APKs** y la de Android **no incluye el EXE**.
- La release de Android v0.3.2 se mantiene intacta: no se ha creado una nueva
  release de Android para acompañar a la versión de PC.
- La release de PC se publica como **no-latest** para que el badge «Latest» de
  Android no cambie. Aun así, aparece en la lista de releases y tiene su propio
  enlace directo.
- GitHub no permite fijar releases de forma nativa. Para que ambas descargas
  queden visibles, hay un **aviso fijado** en Issues:
  [Descargas oficiales de ParDL (PC y Android)](https://github.com/andrespar2/ParDL/issues/1).
  Este índice, los títulos, las etiquetas y la sección **Descargas** del README
  completan la organización estable.

## Enlaces

- PC: https://github.com/andrespar2/ParDL/releases/tag/pc-v0.4.0
- Android: https://github.com/andrespar2/ParDL/releases/tag/v0.3.2

## Historial

| Fecha | Plataforma | Versión | Notas |
|---|---|---|---|
| 2026-10-26 | Windows | 0.4.0 | Primera app de escritorio con navegador, metadatos y letras |
| 2026-10-26 | Android | 0.3.2 | Corrección del interruptor de letras y APK optimizado |
| 2025 | Android | 0.3.1 | Ajustes y navegador |
| 2025 | Android | 0.3.0 | Metadatos y letras |
| 2025 | Windows | 0.1.0 | Versión CMD portable histórica |

> Las fechas son orientativas; consulta cada release para ver el detalle y los
> checksums publicados.
