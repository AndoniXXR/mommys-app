# Tareas Pendientes - Mommys App

## Vista de Post (PostActivity)

### Barra de Acciones del Post
- [ ] **Implementar funcionalidad de la barra de botones** en `item_post_page.xml`
  - La barra ya está en el layout pero los botones no tienen click listeners implementados
  - Botones a implementar:
    - **Upvote**: Llamar a la API para votar positivo
    - **Downvote**: Llamar a la API para votar negativo
    - **Favorite**: Toggle de favorito (agregar/quitar de favoritos)
    - **Comments**: Abrir vista de comentarios del post
    - **Download**: Descargar el archivo del post
    - **More options**: Mostrar menú con opciones adicionales (compartir, abrir en navegador, reportar, etc.)
  - Los click listeners deben estar en `PostPagerAdapter.kt` método `setupActionButtons()`
  - Referencia: Ver cómo lo hace la app original en `di/f1.java` y `ei/e0.java`

---

## Otras tareas pendientes
- [ ] Implementar sistema de comentarios completo
- [ ] Implementar sistema de pools
- [ ] Implementar búsqueda de usuarios
