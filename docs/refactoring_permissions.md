# Documentación de Refactorización de Permisos

Esta documentación describe la arquitectura y diseño aplicados en la refactorización de permisos del sistema, tanto a nivel de funcionalidad como a nivel de datos (atributos), y detalla la decisión de diseño respecto al token JWT.

---

## 1. Refactorización de Permisos a Nivel de Datos (Atributos)

Para homologar la arquitectura con el módulo de usuarios, se aplicaron controles de seguridad a nivel de atributos en los siguientes módulos:
* **Roles**
* **Pacientes**
* **Documentos**
* **Plantillas (Templates)**
* **Tenants**

### Arquitectura de Control de Atributos

La restricción no solo bloquea acciones generales (como crear o actualizar), sino que también regula los campos específicos a los que un usuario puede acceder o editar.

```
[Cliente] ---> [Controller] ---> [Service] ---> [Mapper / DTO]
                     |                |                 |
                PreAuthorize   validateAttribute    JsonInclude
               (Funcionalidad)     (Escritura)       (Lectura)
```

1. **Restricción de Escritura (Creación y Actualización)**:
   * En los servicios de cada módulo, al recibir solicitudes de creación o actualización, se validan los campos proporcionados utilizando el método `currentAuthorities()`.
   * Si el DTO de entrada incluye un campo restringido y el usuario no cuenta con la autoridad específica (por ejemplo, `patient:update:phone`), el servicio lanza inmediatamente una excepción de tipo `AccessDeniedException`.

2. **Restricción de Lectura (Filtrado de Respuestas)**:
   * Los mapeadores (`Mappers`) de cada módulo reciben la lista de autoridades del usuario autenticado.
   * Si el usuario no tiene la autoridad específica para leer un campo (por ejemplo, `document:read:clinical_content`), el mapeador asigna dicho atributo como `null` en el `ResponseDto`.

3. **Ocultación de Campos Nulos en JSON**:
   * Todos los DTOs de salida (`ResponseDto`) se anotaron con `@JsonInclude(JsonInclude.Include.NON_NULL)`. Esto asegura que Jackson no serialice los campos que fueron asignados como `null` debido a falta de permisos, evitando enviar datos vacíos innecesarios en la respuesta JSON final.

---

## 2. Decisión de Diseño del JWT (Roles vs. Permisos)

### El Problema del Tamaño del Token
Originalmente, si se guardaran todas las autoridades individuales (los más de 100 permisos del sistema) dentro de los claims del JWT, el tamaño del token aumentaría significativamente en cada solicitud. Un header HTTP con un JWT excesivamente grande (de varios kilobytes) puede provocar fallas de red, problemas de almacenamiento en cookies/localStorage y un desperdicio de ancho de banda innecesario.

### La Solución Implementada
Para solucionar esto, **el JWT se modificó para almacenar únicamente los nombres de los roles (`roles`)** en lugar del listado detallado de permisos:

* **Generación del Token**:
  En [JwtService.java](file:///home/nak/UAGRM/7moSemestre/SI2/Sistema%20de%20gestion%20documental%20sw/sgd_spring-boot/src/main/java/com/sgd_hc/security/service/JwtService.java#L69-L101), al construir el token de acceso, se extraen y almacenan únicamente los nombres de los roles del usuario:
  ```java
  roles = su.getUser().getRoles().stream()
          .map(com.sgd_hc.users.entity.Role::getName)
          .toList();
  extraClaims.put("roles", roles);
  ```

* **Validación de Autoridades en cada Petición**:
  Durante cada solicitud HTTP, el [JwtAuthenticationFilter.java](file:///home/nak/UAGRM/7moSemestre/SI2/Sistema%20de%20gestion%20documental%20sw/sgd_spring-boot/src/main/java/com/sgd_hc/security/filter/JwtAuthenticationFilter.java) extrae el `username` del token y delega la carga del usuario a `UserDetailsServiceImpl.loadUserByUsername(username)`.
  Este servicio consulta la base de datos para obtener el usuario con sus roles y permisos actualizados en tiempo real.

* **Carga Dinámica en Spring Security**:
  En [SecurityUser.java](file:///home/nak/UAGRM/7moSemestre/SI2/Sistema%20de%20gestion%20documental%20sw/sgd_spring-boot/src/main/java/com/sgd_hc/security/details/SecurityUser.java#L19-L29), el método `getAuthorities()` aplana la relación de roles y permisos y devuelve la lista completa de privilegios:
  ```java
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
      Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
              .flatMap(role -> role.getPermissions().stream())
              .map(permission -> new SimpleGrantedAuthority(permission.getName()))
              .collect(Collectors.toSet());

      user.getRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority(role.getName())));
      return authorities;
  }
  ```

### Ventajas de esta Solución:
1. **Tokens más ligeros**: El tamaño del JWT se mantiene pequeño y constante independientemente del número de permisos creados en la base de datos.
2. **Seguridad en tiempo real**: Si se modifican los permisos de un rol en la base de datos, el cambio tiene efecto inmediato en la siguiente petición del usuario, sin necesidad de esperar a que su token JWT expire para re-emitirse.

---

## 3. Centralización en SecurityUtils

Para evitar código duplicado e idéntico en los servicios, se centralizó la extracción y validación de las autoridades en la clase de utilidad estática [SecurityUtils.java](file:///home/nak/UAGRM/7moSemestre/SI2/Sistema%20de%20gestion%20documental%20sw/sgd_spring-boot/src/main/java/com/sgd_hc/security/utils/SecurityUtils.java):

```java
package com.sgd_hc.security.utils;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

public class SecurityUtils {

    public static Set<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return Set.of();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    public static void requireAuthority(Set<String> authorities, String authority) {
        if (!authorities.contains(authority))
            throw new AccessDeniedException("Missing authority: " + authority);
    }
}
```

### Uso en los Servicios
Todos los servicios refactorizados importan estáticamente estos métodos:
```java
import static com.sgd_hc.security.utils.SecurityUtils.*;
```
Esto permite invocar directamente a `currentAuthorities()` y `requireAuthority(...)` manteniendo el código de los servicios altamente limpio, modular y libre de redundancias.

---

## 4. Alcance y Políticas de Permisos por Tipo de Operación (CRUD)

El control de accesos a nivel de atributos se rige bajo un conjunto de políticas diferenciadas según la naturaleza de la operación para optimizar la seguridad y evitar validaciones innecesarias:

### A. Creación (POST)
* **Atributos Opcionales/Nulables**: Solo se han definido y evaluado permisos para aquellos campos que son opcionales o que la regla de negocio permite que sean nulos al momento de la inserción (por ejemplo, el campo `phone` o la asignación de `roles` en el módulo de usuarios).
* **Atributos Estructurales/Obligatorios**: Los atributos requeridos para la integridad básica del registro (como `username` o `email` en usuarios) no se controlan individualmente a nivel de atributo en la creación, sino que se asumen como parte de la acción funcional de registro general.

### B. Lectura (GET)
* **Granularidad Completa**: Es el nivel de control más restrictivo. Cada atributo individual que se expone en la respuesta de la API cuenta con un permiso de lectura específico (ej. `patient:read:phone`, `document:read:clinical_content`). 
* Si el usuario autenticado carece del permiso de lectura para un campo determinado, el Mapper correspondiente lo setea en `null` y la directiva `@JsonInclude(JsonInclude.Include.NON_NULL)` evita que se renderice en el JSON de salida.

### C. Actualización (PUT/PATCH)
* **Atributos Modificables**: La validación de permisos de escritura durante la actualización se aplica exclusivamente sobre aquellos campos que la lógica de negocio permite modificar directamente al usuario.
* **Exclusión de Campos del Sistema/Inmutables**: Aquellos campos controlados internamente por el sistema o que son inmutables (tales como `id`, `created_at`, `updated_at`, etc.) no poseen permisos a nivel de atributo. La API previene su modificación directa a través de la propia estructura de los DTOs de actualización, los cuales omiten y no mapean estos campos.

### D. Eliminación (DELETE)
* **Control a Nivel Funcional (Endpoint)**: La eliminación de registros se controla de forma exclusiva a nivel funcional/endpoint mediante anotaciones `@PreAuthorize` (ej. `@PreAuthorize("hasAuthority('patient:delete')")`).
* No existen ni se evalúan permisos para "eliminar" atributos individuales, ya que por la estructura relacional de la base de datos no es posible eliminar columnas de forma aislada para un registro en particular.


