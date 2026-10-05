# Changelog

Todos los cambios relevantes de CreditHome se documentan en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/)
y el proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

## [Unreleased]

### Agregado
- Estrategia de ramas Git Flow (`main`, `develop`, `feature/*`, `bugfix/*`, `release/*`, `hotfix/*`) documentada en `CONTRIBUTING.md`.
- Versión de la app centralizada en `gradle.properties` (`APP_VERSION_NAME`, `APP_VERSION_CODE`).
- Registro inicial obligatorio de la cuenta Máster cuando la autenticación está habilitada.
- Validación de robustez del PIN (4–6 dígitos, sin secuencias ni dígitos repetidos).

### Cambiado
- Los secretos de compilación (salt criptográfico, credenciales RabbitMQ) se leen desde `secrets.properties` o variables de entorno por variante.
- `versionName` pasa de `1.0` a formato SemVer `1.0.0`.

### Seguridad
- Eliminada la cuenta `admin` con PIN `1234` por defecto; las instalaciones que la tengan deben registrar una nueva cuenta.
- El login ya no acepta cualquier PIN para usuarios sin PIN registrado.
- Importar un código de invitación ya no sobrescribe usuarios existentes ni asigna PIN `1234` por defecto.
