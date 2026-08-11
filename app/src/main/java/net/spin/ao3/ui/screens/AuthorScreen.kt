package net.spin.ao3.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.spin.ao3.data.AppContainer
import net.spin.ao3.data.model.AuthorProfile
import net.spin.ao3.data.model.WorkSummary
import kotlinx.coroutines.launch
import net.spin.ao3.ui.components.EmptyState
import net.spin.ao3.ui.components.WorkCard
import net.spin.ao3.util.AvatarImages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    container: AppContainer,
    username: String,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
) {
    val context = LocalContext.current
    // Header: small fast request (profile page only, ~20 KB).
    var profile by remember { mutableStateOf<AuthorProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Works: heavier page (~108 KB); loads independently so the header shows
    // as soon as it arrives and the list fills in when ready.
    var works by remember { mutableStateOf<List<WorkSummary>>(emptyList()) }
    var worksCount by remember { mutableStateOf<Int?>(null) }
    var worksError by remember { mutableStateOf<String?>(null) }
    var worksLoading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var worksPage by remember { mutableIntStateOf(1) }
    var noMoreWorks by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    /** Loads the next page of the author's works (AO3 paginates at 20/page). */
    fun loadMoreWorks() {
        if (loadingMore || worksLoading || noMoreWorks) return
        loadingMore = true
        scope.launch {
            try {
                val next = container.client.getAuthorWorks(username, worksPage + 1)
                if (next.works.isEmpty()) {
                    noMoreWorks = true
                } else {
                    worksPage++
                    works = works + next.works.filter { w -> works.none { it.id == w.id } }
                    worksCount = next.count ?: worksCount
                }
            } catch (_: Exception) {
                // Keep the button visible so the user can retry.
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(username, retryTick) {
        loading = true
        error = null
        try {
            profile = container.client.getAuthorProfile(username)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo cargar el perfil"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(username, retryTick) {
        // Reset per-author state: the composable can be reused with a new username.
        works = emptyList()
        worksCount = null
        worksError = null
        worksPage = 1
        noMoreWorks = false
        worksLoading = true
        try {
            val w = container.client.getAuthorWorks(username)
            works = w.works
            worksCount = w.count
        } catch (e: Exception) {
            worksError = e.message ?: "No se pudo cargar"
        } finally {
            worksLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = { Text("Perfil de autor", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val p = profile
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && p == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { retryTick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Reintentar")
                    }
                }
            }
            p == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Perfil no encontrado")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    // ---- Header ----
                    var avatar by remember(p.avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(p.avatarUrl) {
                        avatar = p.avatarUrl?.let { AvatarImages.load(it) }
                    }
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(84.dp),
                        ) {
                            val bmp = avatar
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Foto de perfil de ${p.displayName}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        p.displayName.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            p.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        if (p.displayName != "@${p.username}") {
                            Text(
                                "@${p.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        p.pseuds.takeIf { it.size > 1 }?.let { pseuds ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Pseuds: ${pseuds.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            worksCount?.let { count ->
                                StatCircle("$count", "obras")
                            }
                            p.joined?.let { joined ->
                                StatCircle(joined.take(4), "desde")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (p.bio.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    p.bio,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://archiveofourown.org/users/${p.username}")),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir perfil en AO3")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    SectionTitle("Obras de ${p.displayName}")
                }
                when {
                    worksLoading -> item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    works.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Default.OpenInNew,
                            title = if (worksError != null) "No se pudieron cargar las obras" else "Sin obras visibles",
                            description = worksError
                                ?.let { "Error de red al cargar las obras: $it" }
                                ?: "No se encontraron obras de ${p.displayName} en la primera página.",
                            compact = true,
                            actionLabel = "Reintentar",
                            onAction = { retryTick++ },
                        )
                    }
                    else -> {
                        items(works, key = { it.id }) { work ->
                            WorkCard(
                                work = work,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                onClick = { onOpenDetail(work.id) },
                                onTagClick = { tag -> onOpenTag(tag) },
                                onAuthorClick = null, // ya estamos en el perfil del autor
                            )
                        }
                        val count = worksCount
                        val hasMore = !noMoreWorks && (count == null || works.size < count)
                        if (hasMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        OutlinedButton(onClick = { loadMoreWorks() }) {
                                            Text("Cargar más obras")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCircle(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
