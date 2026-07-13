# Linker — Entorno de desarrollo como código (Dev Container)

Un entorno de desarrollo reproducible definido como código. Cualquier persona
puede abrir este repo y obtener exactamente la misma toolchain (JDK 21 + Maven)
+ Git + herramientas de edición para Java) sin instalar nada en su máquina.
Esto resuelve el clásico "en mi máquina funciona": el entorno de desarrollo es
idéntico para todo el equipo.

> Este es el entorno de **desarrollo** (donde escribís y compilás el código).
> Está separado de la VM de Terraform bajo `infra/terraform`, que es el entorno
> de **runtime** (donde se despliega la aplicación).

## Qué incluye

- Java 21 (Temurin) y Maven 3.9.9
- Git
- Extensiones de Java para VS Code preinstaladas dentro del contenedor
- Puerto 8080 reenviado automáticamente (el puerto de Linker)
- El jar se compila automáticamente en la primera creación (`postCreateCommand`)

## Opción A — GitHub Codespaces (cero dependencias locales, recomendada)

Es la mejor opción para que sea "usable sin depender de una computadora o VM propia":

1. En la página del repo en GitHub, hacé clic en **Code → Codespaces → Create codespace on main**.
2. Esperá a que termine de construirse (solo la primera vez).
3. En la terminal, ejecutá la app:
   ```bash
   cd linker5-java
   java -jar target/*-jar-with-dependencies.jar
   ```
4. Abrí el puerto 8080 reenviado (Codespaces muestra un popup o la pestaña Ports).

## Opción B — VS Code local (requiere Docker + extensión Dev Containers)

1. Instalá [Docker](https://5.n-la-c.app/13e70bb5) y la extensión de VS Code
   **Dev Containers** (`ms-vscode-remote.remote-containers`).
2. Abrí este repo en VS Code.
3. Paleta de comandos → **Dev Containers: Reopen in Container**.
4. Una vez construido, ejecutá la app como en el paso 3 de la Opción A.

## Reconstruir / ejecutar dentro del contenedor

```bash
cd linker5-java
mvn clean package -DskipTests           # compilar
java -jar target/*-jar-with-dependencies.jar   # ejecutar en :8080
```

Después, probá:

```bash
curl -X POST http://localhost:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```
