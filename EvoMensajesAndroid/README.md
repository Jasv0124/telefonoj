# Evo Mensajes — Aplicación Android para Evolution API v2

Proyecto nativo Android, escrito en Java sin librerías de terceros.

## Funciones

- Guardar una URL HTTPS de Evolution API, el **nombre** de la instancia y la API Key.
- Cifrar la clave con **Android Keystore / AES-GCM** antes de guardarla localmente.
- Consultar la conexión mediante `GET /instance/connectionState/{instancia}`.
- Enviar un mensaje individual tras confirmación, mediante `POST /message/sendText/{instancia}`.
- Mostrar el código HTTP, el estado inicial e ID del mensaje si la API los devuelve.
- Borrar la configuración guardada.

## Opción sencilla: compilar en GitHub Actions (sin instalar Android Studio)

1. Crea un repositorio privado en GitHub y sube el contenido de esta carpeta a la rama `main`.
2. En la pestaña **Actions**, busca el flujo **Generar APK Android**.
3. Pulsa **Run workflow** y selecciona la rama `main` (o simplemente realiza un push).
4. Cuando el flujo finalice correctamente, abre la ejecución y en **Artifacts** descarga `EvoMensajes-APK`.
5. Descomprime el archivo recibido y tendrás `app-debug.apk`, que puedes instalar en Android.

Se incluye el flujo `.github/workflows/generar-apk.yml` para realizar la compilación en GitHub.

## Cómo generar el APK con Android Studio

1. Instala **Android Studio** con el SDK de Android 35 (API 35) y JDK 17.
2. Descomprime este ZIP y abre la carpeta `EvoMensajesAndroid` desde **Open**.
3. Permite que Android Studio descargue/prepare **Android Gradle Plugin 8.7.3** y sincronice Gradle (internet necesario).
4. Pulsa **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. El archivo de instalación aparecerá en `app/build/outputs/apk/debug/app-debug.apk`.
6. Pasa el APK al Android e instálalo autorizando la instalación desde esa fuente si se solicita.

Para generar un APK **firmado para distribución**, usa `Build > Generate Signed App Bundle / APK` y conserva de forma segura el almacén de claves de firma.

## Qué poner en la app

- **URL**: `https://evo.tudominio.com` (la URL de la API, sin `/manager`).
- **Instancia**: el nombre exacto de tu instancia, no el ID UUID.
- **API Key**: la clave autorizada de Evolution API.
- **Número**: indicativo + celular sin espacios ni `+`, p. ej. `573001234567`.
- **Mensaje**: texto de hasta 4096 caracteres.

La instancia debe existir y estar conectada a WhatsApp en tu servidor Evolution API.
No necesitas poner tu API Key en el código fuente.

## Limitaciones y seguridad

- Diseñada para **Evolution API v2**, con JSON `{ "number": "573...", "text": "Hola" }`.
- **No crea ni hospeda un servidor Evolution API.** Se conecta a uno que ya tengas.
- **No escanea QR** ni conecta por sí sola el WhatsApp; hazlo en Evolution Manager.
- Solo admite **HTTPS**, para evitar enviar la clave sin cifrar por la red.
- Un HTTP 2xx solo indica que la API aceptó la solicitud; el estado `PENDING` **no garantiza entrega**.
- Si hay una interrupción de red, revisa historial en Evolution antes de reintentar para evitar mensajes duplicados.
- No incluye envíos masivos. Envía únicamente a destinatarios que aceptaron recibir comunicaciones.
- Evita exponer la API Key global de un sistema multiusuario en teléfonos de terceros; usa un backend autenticado para esa arquitectura.

## Verificación

El proyecto contiene el código fuente, recursos y manifiesto de Android. **No se incluye un APK precompilado** porque el entorno en que se generó este ZIP no dispone del SDK/Gradle Android necesarios para compilarlo. El APK debe generarse con Android Studio siguiendo los pasos indicados.
