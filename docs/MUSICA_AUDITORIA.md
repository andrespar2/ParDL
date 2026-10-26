# Auditoría de `registry.json`

Fecha de auditoría: 26/10/2026.

Procedimiento:

1. Descarga de los 12 paquetes desde las URLs del registro.
2. Cálculo de SHA-256.
3. Comparación con el hash declarado.
4. Descompresión en una carpeta aislada.
5. Lectura de `manifest.json`, `LICENSE`, `NOTICE` y tamaño de `index.js`.
6. **No se ejecutó ningún paquete ni su JavaScript.**

## Resultado de integridad

Los 12 hashes coincidieron exactamente con `registry.json`.

| Extensión | Versión | Categoría | SHA-256 |
|---|---|---|---|
| spotify-web | 1.10.2 | integration | `26f4f8de…67f81e` |
| amazon | 2.3.14 | download | `d45e105b…cb967` |
| apple-music | 1.4.14 | integration | `6a45c61e…f86ef17` |
| soundcloud | 1.0.11 | download | `19ff9fda…73ae80b` |
| ytmusic-spotiflac | 2.4.4 | download | `bf88cf55…12f8c1e` |
| deezer | 1.3.7 | download | `39868171…64b464f292` |
| qobuz-web | 1.2.23 | download | `054c1d2c…3ee99985f` |
| tidal-web | 1.2.9 | download | `69ce52ea…25d1d49114d` |
| bugs-music | 2.0.4 | integration | `a20fbc96…97bf4ec6f` |
| melon-music | 2.0.4 | integration | `b40d17d7…1eb3b94f` |
| genie-music | 2.0.4 | integration | `9ae3e250…4becca331` |
| jiosaavn | 1.0.8 | download | `be9a2313…d4e344ec0a92` |

> Un hash correcto solo demuestra que el paquete es el publicado en el
> registro; **no** demuestra que sea seguro ni que su descarga sea legítima.

## Estructura

Todos son archivos ZIP con:

```
manifest.json
index.js          (JavaScript; 25–178 KB)
LICENSE           (LGPL-3.0 en la mayoría; spotify-web no incluye LICENSE)
NOTICE
COPYING
icon.png / icon.jpg (algunos)
```

Dependencias del anfitrión original declaradas en los manifiestos:

- `signedSession@1` / `signedSession@3`
- `sessionGrant@1`
- `downloadSegments@1`
- `preparedContext@1`
- `patternedFileTransform@1`

Son APIs privadas de la aplicación anfitriona original, no estándares web. Sin ese anfitrión y
sin sus servicios de firma/sesión no son portables a ParDL.

## Clasificación

### Solo metadatos

- **Spotify Web**: API de socio y `clienttoken`. Se reemplazó por un adaptador
  propio de oEmbed público para identificar enlaces, sin audio.
- **Apple Music**: `amp-api.music.apple.com`; requiere `mediaUserToken` para
  letras. ParDL usa la API pública de iTunes para metadatos y vista previa.
- **Bugs / Melon / Genie**: scraping de sitios coreanos, sin API pública
  estable. No portados.

### Descarga con resolutor de terceros

- **TIDAL**, **Deezer**, **Qobuz**, **Amazon**: usan `api.zarz.moe` y sesiones
  HMAC. El audio lossless/Atmos se resuelve fuera del proveedor original.
- **JioSaavn**: descarga directa de AAC.
- **SoundCloud**: streams directos.
- **YouTube Music**: audio de YouTube, con PO tokens opcionales.

No se integran: implican acceso no autorizado o dependencia de un servicio de
terceros no verificable. ParDL no ejecuta su código ni implementa sus firmas.

## Decisiones en ParDL

| Extensión | Decisión |
|---|---|
| tidal-web | Preferido visible; descarga **no integrada** (requiere autorización) |
| spotify-web | Adaptador propio de identificación por enlace |
| apple-music | Integración propia con iTunes (metadatos + preview) |
| deezer | Integración propia con API pública (metadatos + preview) |
| ytmusic | Delegado al motor yt-dlp existente para enlaces |
| soundcloud | Delegado al motor yt-dlp existente para enlaces |
| qobuz / amazon / jiosaavn | No compatibles |
| bugs / melon / genie | No portados |

## Licencias

La mayoría declara LGPL-3.0. `spotify-web` no incluye archivo de licencia. No se
copió código de los paquetes; las integraciones de ParDL son adaptadores
propios sobre APIs públicas y documentadas.
