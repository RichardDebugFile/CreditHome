## Descripción

<!-- ¿Qué cambia y por qué? Referencia el ID del backlog si aplica (ej. SEC-02). -->

## Tipo de cambio

- [ ] `feature` — nueva funcionalidad
- [ ] `bugfix` — corrección en develop
- [ ] `release` — preparación de versión
- [ ] `hotfix` — corrección urgente en producción
- [ ] `refactor` / `docs` / `build` / `ci`

## Checklist

- [ ] Rama destino correcta (`develop` para feature/bugfix, `main` para release/hotfix)
- [ ] Compila: `./gradlew assembleDebug`
- [ ] Tests pasan: `./gradlew testDebugUnitTest`
- [ ] `CHANGELOG.md` actualizado en `[Unreleased]`
- [ ] Sin secretos, certificados (`.p12` / `.pfx`) ni keystores en el diff
- [ ] Si es release/hotfix: `APP_VERSION_NAME` y `APP_VERSION_CODE` actualizados en `gradle.properties`

## Cómo probar

<!-- Pasos para verificar el cambio en la app. -->
