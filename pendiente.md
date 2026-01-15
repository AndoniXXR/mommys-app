# Pendiente - Comparación App Original vs Reconstruida

---

## ✅ COMPLETADO

### Activities (7/7 - 100%)
- [x] ChangelogActivity - Historial de cambios
- [x] WikiShowActivity - Mostrar páginas wiki con deep links
- [x] BrowseSetsActivity - Explorar sets de posts
- [x] EditPostActivity - Editar tags/rating de posts (API call implementado)
- [x] DonateActivity - Donaciones (con strings y layout)
- [x] ScrollViewTextActivity - ToS/Privacy Policy/Licenses con WebView
- [x] WebViewActivity - WebView genérico para Cloudflare

### Opciones de Menú Principal (33/33 - 100%)
- [x] Login → LoginActivity
- [x] My Profile → ProfileActivity
- [x] My Favourites → búsqueda "fav:username"
- [x] My Upvotes → búsqueda "votedup:username"
- [x] My Posts → búsqueda "user:username"
- [x] My Comments → WebViewActivity (comments page)
- [x] My DMail → WebViewActivity (dmails)
- [x] My Sets → BrowseSetsActivity (EXTRA_MY_SETS=true)
- [x] My Following → SettingsActivity (open_following=true)
- [x] Logout → prefs.logout()
- [x] Go To Page → showGoToPageDialog()
- [x] Go To Random Post → goToRandomPost()
- [x] Go To User → showGoToUserDialog()
- [x] Popular By Day/Week/Month → PopularActivity
- [x] Browse Following → FollowingPostActivity
- [x] Browse Tags → BrowseTagsActivity
- [x] Browse Pools → BrowsePoolsActivity
- [x] Browse Artists → WebViewActivity (/artists)
- [x] Browse Comments → WebViewActivity (/comments)
- [x] Browse Sets → BrowseSetsActivity
- [x] Browse Wiki → showWikiSearchDialog()
- [x] Browse Blips → WebViewActivity (/blips)
- [x] Browse Users → WebViewActivity (/users)
- [x] Queued Downloads → DownloadManagerActivity
- [x] Followed Tag Posts → FollowingPostActivity
- [x] About → AboutActivity
- [x] Help → showHelpDialog()
- [x] Changelog → ChangelogActivity
- [x] News → NewsActivity
- [x] Donate → DonateActivity
- [x] Licenses → ScrollViewTextActivity (TYPE_LICENSES)
- [x] Translate → Intent URL Crowdin
- [x] Discord → Intent URL Discord
- [x] Send Feedback → Intent email
- [x] Saved Searches → SavedSearchesActivity
- [x] Check Updates → checkForUpdates()
- [x] Settings → SettingsActivity

### Opciones de Menú Post (13/13 - 100%)
- [x] Slideshow → showSlideshowDialog()
- [x] Edit Post → EditPostActivity
- [x] Add to Set → BrowseSetsActivity (SELECT_MODE)
- [x] View Wiki → showWikiTagSelectionDialog() → WikiShowActivity
- [x] Flag Post → WebViewActivity (flag page)
- [x] Open in Browser → Intent browser
- [x] Open in Video Player → Intent video player (solo videos)
- [x] Share Post → showShareDialog()
- [x] Copy Post Link → Clipboard
- [x] Copy Post Tags → Clipboard
- [x] Reload Post → reloadPost()
- [x] Check Notes → NotesActivity
- [x] View JSON → showJsonDialog()

### Post Actions
- [x] Pool Batch Download - Descargar múltiples posts desde PoolActivity
- [x] Edit Post - Editar tags/source/rating con API call PATCH
- [x] Add to Set - Desde PostActivity, lanzar BrowseSetsActivity en modo selección

### Deep Links (14/14 - 100%)
| Path | Activity | Estado |
|------|----------|--------|
| `e621.net/posts/{id}` | PostActivity | ✅ handleDeepLink() con Regex |
| `e926.net/posts/{id}` | PostActivity | ✅ handleDeepLink() con Regex |
| `e621.net/pools/{id}` | PoolActivity | ✅ getPoolId() con pathSegments |
| `e926.net/pools/{id}` | PoolActivity | ✅ getPoolId() con pathSegments |
| `e621.net/users/{username}` | ProfileActivity | ✅ handleDeepLink() con lastPathSegment |
| `e926.net/users/{username}` | ProfileActivity | ✅ handleDeepLink() con lastPathSegment |
| `e621.net/post_sets/{id}` | BrowseSetsActivity | ✅ handleDeepLink() con Regex |
| `e926.net/post_sets/{id}` | BrowseSetsActivity | ✅ handleDeepLink() con Regex |
| `e621.net/posts?tags={query}` | MainActivity | ✅ handleDeepLink() con query params |
| `e926.net/posts?tags={query}` | MainActivity | ✅ handleDeepLink() con query params |
| `e621.net/comments/{id}` | CommentsActivity | ✅ handleDeepLink() con Regex |
| `e926.net/comments/{id}` | CommentsActivity | ✅ handleDeepLink() con Regex |
| `e621.net/wiki_pages/{tag}` | WikiShowActivity | ✅ handleDeepLink() |
| `e926.net/wiki_pages/{tag}` | WikiShowActivity | ✅ handleDeepLink() |

### Storage & Downloads
- [x] Folder Picker - SAF con ActivityResultContracts.OpenDocumentTree() en StorageSettingsFragment
- [x] File Name Mask Editor - Dialog con opciones (%artist%, %id%, %character%, etc.)
- [x] Custom folder URI persistente con takePersistableUriPermission()
- [x] Overwrite/Hide files options
- [x] Cache management (clear, size limit)

### Post UI Features
- [x] Pull-to-close - SlidingPanelLayout con ViewDragHelper para cerrar post arrastrando hacia abajo
  - Zona sensible: 45% superior de la pantalla
  - Threshold de cierre: 25% del alto
  - Preferencia: postPullToClose
  - Animación suave con overlay oscuro

### Export/Import Settings
- [x] Export Settings - Genera JSON con todas las preferencias
- [x] Import Settings - Lee JSON y aplica preferencias
- [x] Encriptación AES/CBC/PKCS5Padding (como app original)
- [x] Formatos: .tws (encriptado), .tws_plain (texto plano)
- [x] Excluye keys sensibles (cookies, api_key, etc.)
- [x] ExportImportManager.kt completo

### Comments
- [x] Reply to comment - showReplyDialog() con quote format [quote]...[/quote]
- [x] Vote Up/Down - POST /comments/{id}/votes.json
- [x] Edit comment - PATCH /comments/{id}.json (solo propios)
- [x] Delete comment - DELETE /comments/{id}.json (solo propios)
- [x] Report comment - Abre navegador con ticket URL
- [x] Copy comment text

### Strings y Recursos
- [x] Todos los strings necesarios para las activities
- [x] Layouts para todas las activities
- [x] Colores para botones de donación
- [x] Activities registradas en AndroidManifest
- [x] licenses_title string agregado

---

## ❌ PENDIENTE

### Traducciones Ruso (values-ru/strings.xml)
Agregar todas las strings de configuración y vista de post que faltan:
- Settings completas (pref_export, pref_cloudflare, pref_ads, pref_privacy, pref_network, pref_following_*, pref_account, pref_about, pref_general_*, pref_grid_*, pref_post_*, pref_search_*, pref_storage_*)
- PIN/Segurança (pin_*, pref_consent_*)
- Pool activity (pool_*, loading_pool, etc.)
- Download manager (download_manager_*, worker_download_*)
- Following posts (following_post_*, following_notification_*)
- Edit post (edit_post_*)
- Slideshow (ss_*)
- Wiki (wiki_*)
- Donate (donate_*)
- About (about_*)
- Update (update_*)
- Arrays extras (arrays_*)
- Saved searches extras (saved_*)
- Blacklist export/import (blacklist_export_*, blacklist_import_*)
- Network status (network_*)
- Page errors extras (page_error_*)
- Report (report_*)

### Funcionalidades Menores (Opcionales)
| Feature | Descripción | Prioridad |
|---------|-------------|-----------|
| Rating change animation | Animación visual al cambiar rating | Muy Baja |
| Note overlay on images | Mostrar notes como overlay en las imágenes | Muy Baja |

---

## Notas

- La app está funcionalmente completa comparada con la app original
- WikiShowActivity ya tiene deep links funcionando para `/wiki_pages/{tag}`
- BrowseSetsActivity soporta modo selección (EXTRA_SELECT_MODE)
- ChangelogActivity está completa
- El menú principal tiene TODOS los items implementados
- El menú de post tiene TODOS los items implementados
- EditPostActivity tiene API call completo para PATCH /posts/{id}.json
- PoolActivity tiene batch download integrado con DownloadQueueService
- CookieWebViewActivity ya existe en ui/settings/
- ScrollViewTextActivity soporta TYPE_LICENSES
- CommentsActivity tiene todas las acciones: Reply, Vote, Edit, Delete, Report, Copy
- ExportImportManager usa encriptación AES igual que la app original
- StorageSettingsFragment tiene folder picker con SAF
- **Pull-to-close** implementado como SlidingPanelLayout usando ViewDragHelper (como df/c.java en app original)
