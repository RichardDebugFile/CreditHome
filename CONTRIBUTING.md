# Guía de Contribución — CreditHome

Este proyecto usa **Git Flow** para el manejo de ramas y **Versionado Semántico (SemVer)** para las versiones de la app.

---

## 🌳 Modelo de ramas

```text
main      ●───────────────●───────────●──────────────  (producción, solo versiones etiquetadas)
           \             / \         /
hotfix/     \           /   ●──●────●                  (correcciones urgentes en producción)
             \         /            \
release/      \   ●──●──●            \                 (estabilización de una versión)
               \ /       \            \
develop   ●─────●─────────●────────────●───────────    (integración, próxima versión)
           \   /   \     /
feature/    ●─●     ●───●                              (nuevas funcionalidades)
bugfix/                                                (errores detectados en develop)
```

| Rama | Sale de | Se integra en | Propósito |
|---|---|---|---|
| `main` | — | — | Código en producción. Cada commit es una versión publicada con tag `vX.Y.Z`. |
| `develop` | `main` | — | Rama de integración. Siempre debe compilar. |
| `feature/<nombre>` | `develop` | `develop` | Nueva funcionalidad o mejora. |
| `bugfix/<nombre>` | `develop` | `develop` | Error encontrado en `develop` (aún no publicado). |
| `release/<X.Y.Z>` | `develop` | `main` + `develop` | Preparar versión: subir número, changelog, solo correcciones menores. |
| `hotfix/<X.Y.Z>` | `main` | `main` + `develop` | Error crítico en producción. |
| `support/<X.Y>` | `main` | — | Mantenimiento de versiones antiguas (solo si se necesita). |

> ❌ Nunca se hace commit directo en `main` ni en `develop`. Todo entra por Pull Request.

### Nombres de rama

- Minúsculas y guiones (`kebab-case`), en español o inglés pero consistente.
- Referenciar el ID del backlog cuando aplique.

```text
feature/sec-02-pin-pbkdf2
feature/export-excel-multi-hoja
bugfix/ocr-fecha-espanol
release/1.1.0
hotfix/1.0.1
```

---

## 🔄 Flujos de trabajo

Se puede trabajar con la extensión [`git flow` (AVH)](https://github.com/petervanderdoes/gitflow-avh) — incluida en Git for Windows — o con comandos git estándar.

**Configuración inicial (una vez por clon):**

```bash
git flow init -d   # Usa main como producción y develop como integración
# Si pregunta por la rama de producción, responder: main
# Prefijo de tags: v
```

### Feature / Bugfix

```bash
git flow feature start sec-02-pin-pbkdf2        # crea feature/sec-02-pin-pbkdf2 desde develop
# ... commits ...
git flow feature publish sec-02-pin-pbkdf2      # push a origin
# Abrir PR: feature/sec-02-pin-pbkdf2 → develop
```

Equivalente sin extensión:

```bash
git switch develop && git pull
git switch -c feature/sec-02-pin-pbkdf2
git push -u origin feature/sec-02-pin-pbkdf2
```

Para errores en `develop` usar `git flow bugfix start <nombre>` (rama `bugfix/...`).

### Release

```bash
git flow release start 1.1.0
# 1. Actualizar APP_VERSION_NAME / APP_VERSION_CODE en gradle.properties
# 2. Mover [Unreleased] → [1.1.0] en CHANGELOG.md
# 3. Probar build qa:  ./gradlew assembleQa
# 4. Solo correcciones menores en esta rama
git flow release finish 1.1.0     # merge a main + tag v1.1.0 + merge a develop
git push origin main develop --tags
```

### Hotfix

```bash
git flow hotfix start 1.0.1
# Corregir, subir APP_VERSION_NAME=1.0.1 y APP_VERSION_CODE (+1), actualizar CHANGELOG
git flow hotfix finish 1.0.1      # merge a main + tag v1.0.1 + merge a develop
git push origin main develop --tags
```

---

## 🏷️ Versionado

Formato **SemVer** `MAJOR.MINOR.PATCH`:

| Incremento | Cuándo | Ejemplo |
|---|---|---|
| `MAJOR` | Cambios incompatibles (formato de backup, migración de datos no reversible) | `1.4.2` → `2.0.0` |
| `MINOR` | Nueva funcionalidad compatible | `1.4.2` → `1.5.0` |
| `PATCH` | Corrección de errores | `1.4.2` → `1.4.3` |

**Fuente única de versión:** [`gradle.properties`](gradle.properties)

```properties
APP_VERSION_NAME=1.1.0   # SemVer, igual al tag sin la "v"
APP_VERSION_CODE=2       # Entero, +1 en cada versión publicada (nunca se reutiliza)
```

- Solo se modifican en ramas `release/*` y `hotfix/*`.
- El tag en `main` es `v` + `APP_VERSION_NAME` (ej. `v1.1.0`).
- La variante `qa` agrega el sufijo `-qa` automáticamente (`1.1.0-qa`).

---

## ✍️ Mensajes de commit

[Conventional Commits](https://www.conventionalcommits.org/es/):

```text
<tipo>(<ámbito opcional>): <descripción en imperativo>

[cuerpo opcional: el porqué del cambio]
```

| Tipo | Uso |
|---|---|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de error |
| `security` | Corrección de seguridad |
| `refactor` | Cambio de código sin alterar comportamiento |
| `perf` | Mejora de rendimiento |
| `test` | Tests |
| `docs` | Documentación |
| `build` | Gradle, dependencias |
| `ci` | Integración continua |
| `chore` | Mantenimiento general |

Ejemplos:

```text
feat(ocr): reconocer fechas en formato "20 de julio de 2026"
fix(amortizacion): corregir redondeo en la última cuota
security(auth): derivar hash de PIN con PBKDF2 y salt por usuario
```

---

## ✅ Pull Requests

- Destino: `develop` (features/bugfixes) o `main` (solo releases/hotfixes).
- El proyecto debe compilar: `./gradlew assembleDebug`.
- Los tests deben pasar: `./gradlew testDebugUnitTest`.
- No incluir secretos, `secrets.properties`, certificados `.p12`/`.pfx` ni keystores.
- Actualizar `CHANGELOG.md` en la sección `[Unreleased]`.
