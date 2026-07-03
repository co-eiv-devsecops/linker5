# Cómo contribuir a Linker

Gracias por sumarte a mejorar Linker.

Este repo tiene una app Java para acortar URLs, scripts de despliegue e infraestructura. La idea es simple: cambios chicos, claros y fáciles de revisar.

## Camino rápido

1. Hacé un fork o trabajá desde una rama nueva.
2. Mantené el cambio enfocado en un solo problema.
3. Corré las validaciones que apliquen.
4. Abrí un pull request con contexto suficiente.

## Cómo levantar el proyecto

### App

```bash
cd linker5-java
mvn test
mvn clean package
```

Para correr la app localmente:

```bash
java -jar target/*-jar-with-dependencies.jar
```

### Infraestructura

Si tocás infra o entorno de trabajo, revisá también:

- `infra/terraform/README.md`
- `.devcontainer/README.md`

## Formas de aportar

Podés ayudar con:

- corrección de bugs
- mejoras de documentación
- nuevas funcionalidades
- tests
- mejoras en despliegue o infraestructura

## Antes de abrir un issue

- Usá los templates de `.github/ISSUE_TEMPLATE/`
- Revisá si alguien ya reportó lo mismo
- Sumá pasos para reproducir, resultado esperado y resultado actual
- Si es un problema de seguridad, NO lo publiques en un issue. Seguí `SECURITY.md`

## Estándar para pull requests

Un PR por cambio lógico. Nada de mezclar veinte cosas distintas en la misma revisión.

### Qué esperamos de un buen PR

- explicar **qué** cambió y **por qué**
- enlazar el issue relacionado si existe
- dejar notas de testing
- no meter refactors no relacionados
- actualizar documentación si cambia el comportamiento o el flujo de trabajo

### Checklist antes de abrir el PR

- [ ] El cambio está enfocado en un problema puntual
- [ ] Agregué o ajusté tests si hacía falta
- [ ] Corrí las validaciones relevantes en local
- [ ] Actualicé documentación si correspondía
- [ ] No subí secretos, credenciales ni datos sensibles

## Expectativas sobre el código

- Priorizá código simple y explícito
- Mantené nombres claros y consistentes
- Evitá cambios de formato sin valor real
- Respetá la estructura actual salvo que haya una buena razón para moverla
- Si cambia el comportamiento, agregá tests cuando sea posible

## Commits

- Escribí mensajes claros
- Mantené los commits enfocados
- No mezcles app, infra y docs sin una razón concreta

## Convivencia del equipo

Al participar en este proyecto, aceptás las pautas de `CODE_OF_CONDUCT.md`.

## ¿Necesitás ayuda?

Empezá por `SUPPORT.md`.
