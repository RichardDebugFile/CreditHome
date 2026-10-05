# 🏠 CreditHome - Sistema Integral de Amortización y Gestión de Créditos

**CreditHome** es una aplicación profesional para Android diseñada para la simulación, cálculo, administración de créditos y control de cobranzas. Cuenta con generación y firma de comprobantes oficiales en PDF, digitalización OCR de depósitos bancarios, centro de notificaciones y respaldo seguro de datos.

---

## 🚀 Características Principales

### 📊 1. Motor Financiero y Simulación de Créditos
- **Cálculo de Amortización**: Generación de tablas de amortización con cuota fija y desglose exacto de capital, interés y saldo restante.
- **Abonos Extraordinarios**: Simulación de pagos anticipados a capital para reducción de plazo o reducción de cuota.
- **Calculadora de Cuota Objetivo**: Cálculo inverso para determinar el plazo en años/meses a partir de una cuota mensual deseada.
- **Interés Moratorio y Puntuabilidad**: Detección de pagos a tiempo, pagos con retraso o cuotas en mora con cálculo de días.

---

### 👥 2. Gestión de Clientes y Cartera de Créditos
- **Perfiles de Deudores**: Registro de nombre, identificación (Cédula/RUC con validación de país), teléfono con selector internacional, correo electrónico y notas.
- **Modo Libre / Multicliente**: Operación como calculadora libre o administración simultánea de múltiples clientes.
- **Barra de Progreso y Estado**: Visualización del avance del crédito (ej. *2 de 60 cuotas pagadas*) con insignias de estado y ordenamiento inteligente.

---

### 📄 3. Comprobantes Oficiales, Vista Previa y Exportación
- **Generador de PDF Vectorial**: Emisión de comprobantes de pago de alta calidad con membrete de la empresa, desglose financiero, datos del titular y firma autorizada.
- **Visor PDF Interactivo en Tiempo Real**: Vista previa con zoom dinámico (hasta 500%), ajuste de escala, panorámica y modo pantalla completa.
- **Personalización de Vouchers**: Editor para activar/ocultar campos (RUC, dirección, saldos anteriores/restantes, notas personalizadas y campos a medida).
- **Exportación a Microsoft Excel (.xlsx)**: Descarga completa de la tabla de amortización y pagos en hojas de cálculo.
- **Compartir por WhatsApp**: Envío rápido de resúmenes de pago y cuotas pendientes formateadas en texto listo para mensajería.

---

### ✍️ 4. Módulo Avanzado de Firmas (Digital, Electrónica y Física)
- **Firma Táctil en Pantalla**: Lienzo digital para firma a mano alzada con encuadre automático dinámico (*bounding box*) que evita cortes y genera trazos nítidos con fondo transparente.
- **Firma Electrónica (.p12 / .pfx)**: Validación criptográfica de certificados digitales (Security Data, Banco Central del Ecuador, ANFAC, etc.) con generación de sello de seguridad oficial.
- **Digitalización de Firma en Papel / Foto**: Captura fotográfica de firmas físicas con **remoción inteligente de fondo blanco** para estampar la tinta transparente o conservar el fondo original.

---

### 🔍 5. OCR Inteligente de Comprobantes de Pago
- **Escaneo Automático de Comprobantes**: Reconocimiento de texto (ML Kit) para autocompletar:
  - Monto transferido / depositado.
  - Fecha y hora del depósito (con soporte de fechas en español como *"20 de julio de 2026"*).
  - Número de referencia / transacción.
  - Banco o entidad financiera origen/destino.
  - Nombre del titular / depositante.
- **Soporte Multicomprobante**: Posibilidad de adjuntar múltiples imágenes de respaldo por cada cuota registrada.

---

### 🔔 6. Centro de Notificaciones y Alertas de Cobro
- **Alertas de Vencimiento**: Detección automática de cuotas próximas a vencer (dentro de 7 días) y cuotas vencidas en mora.
- **Notificaciones Push del Sistema**: Integración con la barra de estado de Android con icono corporativo de CreditHome.
- **Panel de Control**: Filtrado por tipo de notificación, descarte individual y botón de lectura masiva (*"Leer todas"*).
- **Deduplicación Inteligente**: Prevención de alertas repetidas por cuota y limpieza automática de alertas al confirmarse el pago.

---

### 🛡️ 7. Arquitectura, Seguridad y Feature Flags
- **Offline-First (Modo Local Independiente)**: Funcionamiento 100% autónomo sin requerir conexión a internet ni dependencias de servidor externo.
- **Copia de Seguridad y Restauración**: Exportación e importación segura de la base de datos completa en formato JSON.
- **Sistema de Feature Flags (`features.json`)**: Configuración modular para activar o desactivar funciones avanzadas (actualizaciones VPS, auditoría, sincronización RabbitMQ, correo SMTP, autenticación multiusuario).
- **Autenticación sin credenciales por defecto**: Al activar `auth_and_roles_enabled`, el primer arranque obliga a registrar la cuenta Máster con un PIN propio (4–6 dígitos, sin secuencias ni dígitos repetidos). No existe ningún usuario/PIN predeterminado.

---

## 🛠️ Tecnologías y Librerías Utilizadas

| Componente | Tecnología |
|---|---|
| **Lenguaje** | Kotlin |
| **Interfaz de Usuario** | Jetpack Compose (Material Design 3) |
| **Arquitectura** | Clean Architecture (Data - Domain - UI) con MVVM |
| **Estado y Concurrencia** | Kotlin Coroutines & StateFlow |
| **Generación de PDF** | Android `PdfDocument` nativo & `PdfRenderer` |
| **Firma Electrónica** | Certificados PKCS#12 (`.p12` / `.pfx`) con firma PAdES embebida en el PDF |
| **Reconocimiento de Texto (OCR)** | Google ML Kit Text Recognition |
| **Procesamiento de Imágenes** | Canvas 2D, BitmapFactory, Color Matrix & Pixel Processing |
| **Persistencia** | SharedPreferences & archivos JSON locales (backups cifrados con AES-256-GCM) |
| **Build** | Gradle Kotlin DSL, AGP 9.1, Version Catalog (`gradle/libs.versions.toml`) |

---

## 📂 Estructura del Proyecto

```text
app/src/main/java/com/alfas/amortizacioncredito/
├── config/                  # Configuraciones globales de la app
├── data/
│   ├── feature/             # FeatureFlagManager y llaves de activación
│   ├── model/               # Modelos de datos (ClientProfile, PaymentRecord, VoucherConfig, etc.)
│   └── repository/          # Repositorios locales (ClientRepository, NotificationRepository, etc.)
├── domain/
│   ├── bot/                 # Asistente de pagos (parser de intención y calendario)
│   ├── security/            # Firma P12/PAdES, bóveda de firmas, QR y SecurityManager
│   ├── service/             # AppUpdateManager, SMTP, RabbitMQ
│   └── usecase/             # Casos de uso (Cálculo amortización, OCR, Exportación PDF/Excel, backups, etc.)
├── ui/
│   ├── components/          # Componentes reutilizables de UI (PhoneInput, visores, badges)
│   ├── dialogs/
│   │   ├── admin/           # Centro de notificaciones, auditoría, usuarios, SMTP, RabbitMQ, backups
│   │   ├── bot/             # Asistente conversacional de pagos
│   │   ├── business/        # Perfil de empresa, firmas, personalización de voucher
│   │   ├── client/          # Listado y guardado de clientes
│   │   ├── common/          # Diálogos compartidos (recorte de imagen)
│   │   └── payment/         # Registro de pagos y previsualización de comprobantes
│   ├── screens/             # Splash, Login, Dashboard y AmortizationScreen
│   ├── theme/               # Paleta de colores, tipografía y estilos Material 3
│   └── AmortizationViewModel.kt # ViewModel principal y orquestador de estado
```

---

## ⚙️ Requisitos de Compilación

- **Android Studio**: versión compatible con Android Gradle Plugin 9.1.
- **JDK**: Java 17.
- **Min SDK**: API 28 (Android 9 Pie).
- **Target / Compile SDK**: API 36.

### Variantes de compilación

| Variante | Uso |
|---|---|
| `debug` | Desarrollo local |
| `qa` | Pruebas (sufijo de versión `-qa`) |
| `release` | Producción (requiere secretos configurados) |

---

## 🔐 Configuración de Secretos

Los secretos **no** se versionan. Se leen desde `secrets.properties` (raíz del proyecto, ignorado por git) o desde variables de entorno.

```bash
cp secrets.properties.example secrets.properties
# Edita secrets.properties con tus valores
```

| Clave | Descripción | Obligatoria |
|---|---|---|
| `<VARIANTE>_APP_CRYPTO_SALT` | Salt de la bóveda AES para cifrar backups. Cambiarlo impide descifrar backups anteriores. | Sí, en `release` |
| `<VARIANTE>_RABBITMQ_USER` / `_PASSWORD` | Credenciales de RabbitMQ (solo si `rabbitmq_sync_enabled`) | No |

`<VARIANTE>` es `DEBUG`, `QA` o `RELEASE`. Si no existe la clave con prefijo, se usa la clave sin prefijo (ej. `APP_CRYPTO_SALT`). Sin `secrets.properties`, `debug` compila con valores de desarrollo inseguros; `release` falla a propósito.

> Nunca subas certificados (`.p12`, `.pfx`), keystores (`.jks`, `.keystore`) ni `secrets.properties`. Ya están en `.gitignore`.

---

## 🌳 Ramas y Versionado

El proyecto usa **Git Flow** y **SemVer**:

- `main` → producción (cada versión con tag `vX.Y.Z`)
- `develop` → integración de la próxima versión
- `feature/*`, `bugfix/*`, `release/*`, `hotfix/*` → ramas de trabajo

La versión de la app se define en [`gradle.properties`](gradle.properties) (`APP_VERSION_NAME`, `APP_VERSION_CODE`). Detalles del flujo, convenciones de commits y Pull Requests en [CONTRIBUTING.md](CONTRIBUTING.md). Historial de cambios en [CHANGELOG.md](CHANGELOG.md).

---

## 📄 Licencia

Desarrollado para la gestión y administración financiera privada de **CreditHome**. Todos los derechos reservados.
