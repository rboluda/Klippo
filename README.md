<p align="center">
  <img src="docs/icono/klipo_icon.png" width="128" alt="Icono de Klippo">
</p>

<h1 align="center">Klippo</h1>

<p align="center"><b>Español</b> · <a href="README.en.md">English</a></p>

<p align="center">
  <b>Descarga, recorta y comparte vídeos de YouTube, TikTok, X, Instagram y Facebook.</b><br>
  Sin publicidad. Sin cuentas. Sin restos en tu galería.<br>
  Los vídeos se borran solos: Klippo vacía su caché cada hora.
</p>

<p align="center">
  <a href="https://github.com/rboluda/Klippo/releases/latest"><b>⬇️ Descargar la última versión (APK)</b></a>
</p>

<p align="center">
  <img src="docs/capturas/1-inicio.jpg" width="200" alt="Pantalla de inicio vacía">
  &nbsp;
  <img src="docs/capturas/2-calidad.jpg" width="200" alt="Elegir la calidad antes de descargar">
  &nbsp;
  <img src="docs/capturas/3-lista.jpg" width="200" alt="Vídeo descargado con sus accesos directos">
  &nbsp;
  <img src="docs/capturas/4-ajustes.jpg" width="104" alt="Ajustes">
</p>
<p align="center">
  <sub>Inicio · Calidad en cada vídeo · Accesos directos: WhatsApp, compartir, galería y editar · Ajustes</sub>
</p>

---

## Por qué Klippo

La mayoría de apps para descargar vídeos están llenas de anuncios, piden permisos de más
y te dejan la galería llena de archivos que solo querías reenviar una vez. Klippo hace
lo contrario:

- **Sencilla.** Una pantalla con un campo para el enlace y la lista de tus vídeos.
  Pegas, eliges calidad y listo. O, todavía más rápido, compartes el vídeo desde
  la app de origen y Klippo empieza a descargarlo.
- **Limpia.** Los vídeos se guardan solo dentro de Klippo, en su caché privada, y **se
  borran solos**: cada vez que abres la app, Klippo vacía todo lo que tenga más de una
  hora. Nada se acumula en tu móvil y a tu galería solo llega lo que tú decidas guardar.
  Si tienes prisa, *Ajustes → Almacenamiento → Vaciar* lo borra al momento.
- **Privada.** Sin publicidad, sin analítica, sin Firebase, sin cuentas ni pantallas de
  bienvenida. Klippo solo se conecta para descargar el vídeo que le pides y, una vez al
  día, para mirar si hay una versión nueva (se puede desactivar).
- **Rápida.** De la app de origen a WhatsApp en dos toques, sin pasar por la galería.

## Qué hace

**Descargar**
- YouTube, TikTok, X, Instagram y Facebook (y muchas webs más gracias a yt-dlp).
- Carruseles de Instagram y X: se descargan todos sus vídeos, cada uno por separado.
- Pega el enlace (se autorrellena desde el portapapeles) o usa *Compartir → Klippo* desde
  la app de origen: Klippo descarga el vídeo y, al terminar, abre el editor.
- Al acabar cada descarga, la notificación trae *WhatsApp*, *Compartir* y *Guardar*.
- Eliges la calidad en cada vídeo: mejor disponible, 1080p, 720p, 480p o **solo audio (M4A)**.
  Prefiere H.264 para que el vídeo se vea y se comparta sin problemas en cualquier móvil.
- Progreso en una notificación, con opción de cancelar.
- Para los vídeos que piden sesión (Instagram, X, Facebook) puedes iniciarla una vez desde la propia
  app. Klippo nunca ve tu contraseña: escribes en la web oficial y solo se guardan las cookies.

**Editar**
- Recortar el inicio y el final, arrastrando o escribiendo el tiempo exacto (a la décima),
  con vista previa en bucle del tramo elegido.
- Encuadre en 9:16, 1:1, 4:5 o libre: mueve y ajusta el marco sobre el vídeo.
- Texto encima del vídeo, con contorno o con fondo.
- Emojis encima del vídeo: arrástralos, pellízcalos para cambiar el tamaño.
- Quitar el sonido del vídeo exportado.
- Pixelar zonas fijas (caras, matrículas…): dibuja una o varias zonas y ajústalas.
- Todo se aplica de una vez al exportar, con FFmpeg, en un vídeo H.264 + AAC.

**Compartir**
- Accesos directos en cada vídeo: enviar por WhatsApp, compartir con cualquier app,
  guardar en la galería o editar.
- *Guardar en galería* crea `Movies/Klippo/klippo_<fecha>.mp4` (o `Music/Klippo/…m4a` si es
  solo audio). Si no lo pulsas, el vídeo nunca sale de Klippo.

**A tu gusto**
- Lista o mosaico con miniaturas.
- Tema claro, oscuro o del sistema; colores de Klippo o los de tu fondo de pantalla.
- Ajustes para el motor de descarga, cuentas, almacenamiento, notificaciones y actualizaciones.

## Instalación

Klippo no está en Google Play: se instala desde su APK.

1. Descarga el archivo `klippo-….apk` de la [última versión](https://github.com/rboluda/Klippo/releases/latest).
2. Ábrelo en el móvil y permite instalar apps desde ese origen cuando Android lo pida.

Requisitos: Android 13 o superior en un móvil de 64 bits (arm64).

## Actualizaciones

Klippo comprueba una vez al día si hay versión nueva en este repositorio y te lo avisa en
la pantalla de inicio. Pulsas **Actualizar**, confirmas en el instalador de Android y listo.
También puedes buscarla a mano en *Ajustes → Actualizaciones* o desactivar la comprobación.

El motor de descarga (yt-dlp) se actualiza aparte desde *Ajustes → Motor de descarga*:
las plataformas cambian a menudo y una versión reciente del motor suele arreglar cualquier
descarga que falle.

## Compilar

Necesitas Android Studio (incluye el JDK y el SDK; hace falta la plataforma Android 17, API 37).

```bash
./gradlew assembleDebug
```

El APK queda en `apk/`. Klippo usa versiones semánticas (`MAYOR.MENOR.PARCHE`, definidas en
`app/build.gradle.kts`); las compilaciones de depuración añaden la fecha y la hora para
distinguirlas. La app está en español y en inglés, según el idioma del móvil.

Tecnología: Kotlin, Jetpack Compose y Material 3, Media3 (ExoPlayer) para la vista previa,
[youtubedl-android](https://github.com/yausername/youtubedl-android) para empaquetar yt-dlp
y FFmpeg.

## Créditos

Klippo es posible gracias a estos proyectos de software libre:

| Proyecto | Licencia |
| --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense |
| [FFmpeg](https://ffmpeg.org) | GPL |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | GPL-3.0 |
| [Jetpack Compose · Media3](https://developer.android.com/jetpack) | Apache 2.0 |

Respeta los derechos de autor y las condiciones de cada plataforma: descarga solo
contenido que tengas derecho a guardar.

## Licencia

Klippo es software libre bajo la [GNU General Public License v3.0 o posterior](LICENSE)
(GPL-3.0-or-later): puedes usarlo, estudiarlo, modificarlo y redistribuirlo, siempre que
las versiones que distribuyas sigan siendo libres y con su código disponible. Es la licencia
que exigen las piezas que incluye, como youtubedl-android y FFmpeg con libx264.

---

<p align="center">Hecho por <a href="https://github.com/rboluda">rboluda</a></p>
