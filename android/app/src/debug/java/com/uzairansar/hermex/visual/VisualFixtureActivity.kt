package com.uzairansar.hermex.visual

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.uzairansar.hermex.R
import com.uzairansar.hermex.data.preferences.AppThemeMode
import com.uzairansar.hermex.data.preferences.StreamingSendBehavior
import com.uzairansar.hermex.core.model.KanbanBoardSnapshot
import com.uzairansar.hermex.core.model.KanbanCardSummary
import com.uzairansar.hermex.core.model.KanbanColumn
import com.uzairansar.hermex.core.model.ModelSummary
import com.uzairansar.hermex.core.model.SessionSummary
import com.uzairansar.hermex.ui.chat.ChatTopBar
import com.uzairansar.hermex.ui.chat.ChatUiState
import com.uzairansar.hermex.ui.chat.ComposerSurface
import com.uzairansar.hermex.ui.chat.MarkdownText
import com.uzairansar.hermex.ui.kanban.KanbanAvailability
import com.uzairansar.hermex.ui.kanban.KanbanBoardContent
import com.uzairansar.hermex.ui.kanban.KanbanLabUiState
import com.uzairansar.hermex.ui.onboarding.OnboardingWelcomePage
import com.uzairansar.hermex.ui.sessions.SessionRow
import com.uzairansar.hermex.ui.settings.SettingsPickerRow
import com.uzairansar.hermex.ui.settings.SettingsSection
import com.uzairansar.hermex.ui.settings.SettingsToggleRow
import com.uzairansar.hermex.ui.theme.HermesHeaderLogo
import com.uzairansar.hermex.ui.theme.HermexColors
import com.uzairansar.hermex.ui.theme.HermexGlassShape
import com.uzairansar.hermex.ui.theme.HermexIconButton
import com.uzairansar.hermex.ui.theme.HermexPillButton
import com.uzairansar.hermex.ui.theme.LocalHermexHapticsEnabled
import com.uzairansar.hermex.ui.theme.HermexTheme
import com.uzairansar.hermex.ui.theme.hermexGlass
import com.uzairansar.hermex.ui.theme.hermexHazeSource

object VisualFixtureContract {
    const val ExtraFixture = "hermex.visual.fixture"
    const val CatalogTag = "visual_fixture_catalog"
    const val OnboardingCatalogTag = "visual_fixture_catalog_onboarding_welcome"
    const val FrostedCatalogTag = "visual_fixture_catalog_frosted_surface"
    const val ChatCatalogTag = "visual_fixture_catalog_chat"
    const val SessionCatalogTag = "visual_fixture_catalog_session"
    const val KanbanCatalogTag = "visual_fixture_catalog_kanban"
    const val SettingsCatalogTag = "visual_fixture_catalog_settings"
    const val OnboardingRootTag = "visual_fixture_onboarding_welcome"
    const val OnboardingHeroTag = "visual_fixture_onboarding_hero"
    const val FrostedRootTag = "visual_fixture_frosted_surface"
    const val FrostedHeaderTag = "visual_fixture_frosted_header"
    const val FrostedComposerTag = "visual_fixture_frosted_composer"
    const val SessionRootTag = "visual_fixture_session"
    const val KanbanRootTag = "visual_fixture_kanban"
    const val SettingsRootTag = "visual_fixture_settings"
    const val ChatRootTag = "visual_fixture_chat"
}

enum class VisualFixture(val id: String) {
    OnboardingWelcome("onboarding-welcome"),
    FrostedSurface("frosted-surface"),
    Chat("chat"),
    Session("session"),
    Kanban("kanban"),
    Settings("settings");

    companion object {
        fun fromId(id: String?): VisualFixture? = entries.firstOrNull { it.id == id }
    }
}

class VisualFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialFixture = VisualFixture.fromId(intent.getStringExtra(VisualFixtureContract.ExtraFixture))
        setContent {
            HermexTheme(themeMode = AppThemeMode.System) {
                CompositionLocalProvider(LocalHermexHapticsEnabled provides false) {
                    VisualFixtureCatalog(initialFixture = initialFixture)
                }
            }
        }
    }
}

@Composable
private fun VisualFixtureCatalog(initialFixture: VisualFixture?) {
    // Fixture captures are intentionally ephemeral; recreation must always return to the catalog.
    var selectedFixture by remember { mutableStateOf(initialFixture) }
    when (selectedFixture) {
        VisualFixture.OnboardingWelcome -> OnboardingWelcomeFixture()
        VisualFixture.FrostedSurface -> FrostedSurfaceFixture()
        VisualFixture.Chat -> ChatFixture()
        VisualFixture.Session -> SessionFixture()
        VisualFixture.Kanban -> KanbanFixture()
        VisualFixture.Settings -> SettingsFixture()
        null -> FixtureCatalog(onSelect = { selectedFixture = it })
    }
}

@Composable
private fun FixtureCatalog(onSelect: (VisualFixture) -> Unit) {
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.CatalogTag),
        backgroundColor = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HermesHeaderLogo(
                modifier = Modifier
                    .width(126.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Visual fixture catalog",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Debug-only deterministic surfaces for local Compose capture.",
                color = Color.White.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyMedium,
            )
            FixtureCatalogRow(
                tag = VisualFixtureContract.OnboardingCatalogTag,
                title = "Onboarding welcome",
                detail = "Hero artwork, parity copy, badges, and glass actions",
                onClick = { onSelect(VisualFixture.OnboardingWelcome) },
            )
            FixtureCatalogRow(
                tag = VisualFixtureContract.FrostedCatalogTag,
                title = "Frosted surface smoke",
                detail = "Neutral layered chrome over the iOS-matched black backdrop",
                onClick = { onSelect(VisualFixture.FrostedSurface) },
            )
            FixtureCatalogRow(
                tag = VisualFixtureContract.ChatCatalogTag,
                title = "Production chat",
                detail = "Real chat chrome, Markdown, and composer in a deterministic state",
                onClick = { onSelect(VisualFixture.Chat) },
            )
            FixtureCatalogRow(
                tag = VisualFixtureContract.SessionCatalogTag,
                title = "Production session row",
                detail = "Real session list component in selected and streaming states",
                onClick = { onSelect(VisualFixture.Session) },
            )
            FixtureCatalogRow(
                tag = VisualFixtureContract.KanbanCatalogTag,
                title = "Production Kanban",
                detail = "Real Board content with filter and card interaction states",
                onClick = { onSelect(VisualFixture.Kanban) },
            )
            FixtureCatalogRow(
                tag = VisualFixtureContract.SettingsCatalogTag,
                title = "Production settings",
                detail = "Real settings rows at large-font-safe touch targets",
                onClick = { onSelect(VisualFixture.Settings) },
            )
        }
    }
}

@Composable
private fun FixtureCatalogRow(
    tag: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hermexGlass()
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(HermexColors.Gold.copy(alpha = 0.16f))
                .border(1.dp, HermexColors.Gold.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = HermexColors.GoldBright, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("\u203a", color = Color.White.copy(alpha = 0.5f), fontSize = 26.sp)
    }
}

@Composable
private fun OnboardingWelcomeFixture() {
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.OnboardingRootTag),
        backgroundColor = Color.Black,
    ) {
        Box(Modifier.fillMaxSize().testTag(VisualFixtureContract.OnboardingHeroTag)) {
            OnboardingWelcomePage()
        }
    }
}

@Composable
private fun FrostedSurfaceFixture() {
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.FrostedRootTag),
        backgroundColor = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hermexGlass(shape = RoundedCornerShape(26.dp))
                    .testTag(VisualFixtureContract.FrostedHeaderTag)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HermexIconButton(label = "Back", symbol = "\u2039", onClick = {})
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hermex", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Local visual fixture",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HermexIconButton(label = "Session actions", symbol = "\u22ef", onClick = {})
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hermexGlass(shape = RoundedCornerShape(18.dp), castsShadow = false)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6AB8FF)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pinned context",
                        color = Color(0xFF8DCBFF),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Android frosted chrome remains subtle over content.",
                        color = Color.White.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(0.25f))
            FrostedMessageCard(
                label = "YOU",
                message = "Match the iOS flow, while keeping the Android material language native.",
                alignEnd = true,
            )
            FrostedMessageCard(
                label = "HERMEX",
                message = "The glass is restrained: true blur, a neutral low-opacity tint, a hairline border, and layered depth over black.",
                alignEnd = false,
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hermexGlass(shape = RoundedCornerShape(28.dp))
                    .testTag(VisualFixtureContract.FrostedComposerTag)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HermexIconButton(label = "Attach", symbol = "+", onClick = {})
                Text(
                    text = "Message Hermex",
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.42f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                HermexIconButton(
                    label = "Send",
                    symbol = "\u2191",
                    onClick = {},
                    filled = true,
                    filledContainerColor = HermexColors.GoldBright,
                    filledContentColor = Color(0xFF15100A),
                )
            }
        }
    }
}

@Composable
private fun FrostedMessageCard(
    label: String,
    message: String,
    alignEnd: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (alignEnd) 42.dp else 0.dp, end = if (alignEnd) 0.dp else 30.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .hermexGlass(shape = RoundedCornerShape(22.dp), castsShadow = false)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                label,
                color = if (alignEnd) HermexColors.GoldBright else Color(0xFF8DCBFF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                message,
                color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (alignEnd) "10:06  \u2713\u2713" else "10:07",
                modifier = Modifier.align(Alignment.End),
                color = Color.White.copy(alpha = 0.36f),
                fontSize = 10.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ChatFixture() {
    val model = ModelSummary(id = "gpt-5.6", label = "GPT-5.6")
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.ChatRootTag),
    ) {
        Column(Modifier.fillMaxSize()) {
            ChatTopBar(
                title = "Android motion audit",
                subtitle = "Local production fixture",
                hasRepository = true,
                showsFilesButton = true,
                showsGitControls = true,
                onBack = {},
                onOpenWorkspace = {},
                onOpenGit = {},
                canClearConversation = true,
                onClearConversation = {},
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("YOU", color = HermexColors.GoldBright, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                MarkdownText("Make Android feel native, deliberate, and calm.")
                Text("HERMEX", color = Color(0xFF8DCBFF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                MarkdownText("**Streaming stays readable** while the view follows only when you are already at the bottom.")
            }
            ComposerSurface(
                state = ChatUiState(
                    draft = "Review the motion timings",
                    selectedModel = model,
                    modelOptions = listOf(model),
                ),
                isVoiceDictating = false,
                isVoiceDictationTranscribing = false,
                voiceDictationError = null,
                streamingSendBehavior = StreamingSendBehavior.Queue,
                primaryActionTintColor = null,
                showSecondaryBar = true,
                onDraftChange = {},
                onSend = {},
                onStreamingSend = {},
                onCancel = {},
                onOpenModelPicker = {},
                onOpenProfilePicker = {},
                onOpenReasoningPicker = {},
                onOpenWorkspacePicker = {},
                onLoadWorkspaceSuggestions = {},
                onAttach = {},
                onVoiceDictation = {},
                onVoiceNote = {},
                onStopVoiceNote = {},
                onCancelVoice = {},
                onRemoveAttachment = {},
                loadAttachmentImage = { null },
                loadAttachmentFile = { null },
            )
        }
    }
}

@Composable
private fun SessionFixture() {
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.SessionRootTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sessions",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            SessionRow(
                session = SessionSummary(
                    sessionId = "fixture-streaming",
                    title = "Polish Android motion without losing native behavior",
                    workspace = "/workspace/hermex",
                    model = "GPT-5.6",
                    messageCount = 48,
                    isStreaming = true,
                    pinned = true,
                ),
                projects = emptyList(),
                isMutating = false,
                isViewingCachedData = false,
                showsMessageCount = true,
                showsWorkspace = true,
                selected = true,
                actionsExpanded = true,
                onOpen = {},
                onToggleActions = {},
                onPin = {},
                onArchive = {},
                onCopyTitle = {},
                onCopyDeepLink = {},
                onRename = {},
                onDelete = {},
                onBranch = {},
                onDuplicate = {},
                onMove = {},
                onExport = {},
            )
        }
    }
}

@Composable
private fun KanbanFixture() {
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.KanbanRootTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            KanbanBoardContent(
                state = KanbanLabUiState(
                    availability = KanbanAvailability.Content,
                    selectedStatus = "ready",
                    snapshot = KanbanBoardSnapshot(
                        columns = listOf(
                            KanbanColumn(
                                name = "ready",
                                cards = listOf(
                                    KanbanCardSummary(
                                        cardId = "CARD-42",
                                        title = "Refine the Android interaction language",
                                        status = "ready",
                                        assignee = "builder",
                                        priority = 1,
                                        commentCount = 3,
                                        ageSeconds = 3_600.0,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                onRefresh = {},
                onSearch = {},
                onSelectStatus = {},
                onClearFilters = {},
                onOpenCard = {},
            )
        }
    }
}

@Composable
private fun SettingsFixture() {
    FixtureBackdrop(
        modifier = Modifier
            .fillMaxSize()
            .testTag(VisualFixtureContract.SettingsRootTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Settings",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            SettingsSection(title = "Interaction") {
                SettingsToggleRow(label = "Haptic Feedback", value = true, onValueChange = {})
                SettingsPickerRow(
                    label = "Send While Responding",
                    value = "Queue",
                    iconRes = R.drawable.ic_hermex_message_arrow,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun FixtureBackdrop(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .hermexHazeSource(zIndex = 1f, key = "visual-fixture-backdrop")
                .background(backgroundColor),
        )
        content()
    }
}
