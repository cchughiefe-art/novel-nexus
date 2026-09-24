package com.novelnexus.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.novelnexus.app.AppGraph
import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.data.reader.ReaderPrefs
import com.novelnexus.app.data.worker.NovelDownloadWorker
import com.novelnexus.app.ui.reader.ReaderKeyRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return c as? Activity
}

private data class ReaderPalette(val bg: Color, val fg: Color, val muted: Color)
private fun palette(name: String): ReaderPalette = when (name) {
    "DARK" -> ReaderPalette(Color(0xFF171719), Color(0xFFF4F0EB), Color(0xFFAAA39B))
    "SEPIA" -> ReaderPalette(Color(0xFFF0E4CC), Color(0xFF34291D), Color(0xFF776855))
    "PAPER" -> ReaderPalette(Color(0xFFFFF8EC), Color(0xFF251F18), Color(0xFF72685E))
    "WHITE" -> ReaderPalette(Color.White, Color(0xFF181818), Color(0xFF666666))
    else -> ReaderPalette(Color(0xFF050506), Color(0xFFF2F0EB), Color(0xFF9E9991))
}

private fun enqueueRange(context: Context, ref: ChapterRef, from: Int, to: Int) {
    val input = Data.Builder()
        .putString("sourceId", ref.sourceId)
        .putString("novelUrl", ref.novelUrl)
        .putString("title", "Reader download")
        .putInt("from", from)
        .putInt("to", to)
        .build()
    val request = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(input)
        .addTag("novel-download")
        .build()
    WorkManager.getInstance(context).enqueue(request)
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    graph: AppGraph,
    sourceId: String,
    novelUrl: String,
    chapterUrl: String,
    title: String,
    index: Int,
    onBack: () -> Unit,
    onNavigateChapter: (ChapterRef) -> Unit
) {
    val context = LocalContext.current
    val activity = context.activity()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs by graph.readerPreferences.flow.collectAsState(initial = ReaderPrefs())
    val colors = palette(prefs.theme)

    var content by remember(chapterUrl) { mutableStateOf<ChapterContent?>(null) }
    var chapters by remember(novelUrl) { mutableStateOf<List<ChapterRef>>(emptyList()) }
    var downloaded by remember(novelUrl) { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember(chapterUrl) { mutableStateOf<String?>(null) }
    var origin by remember(chapterUrl) { mutableStateOf("Loading") }
    var showChrome by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var newChapterCount by remember { mutableIntStateOf(0) }
    var refreshingChapters by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val sessionStarted = remember(chapterUrl) { System.currentTimeMillis() }

    LaunchedEffect(sourceId, novelUrl, chapterUrl) {
        error = null
        val current = ChapterRef(sourceId, novelUrl, title, chapterUrl, index)
        runCatching { graph.repository.chapterLoad(current) }
            .onSuccess { content=it.content; origin=it.origin }
            .onFailure { error=it.message ?: "Could not load chapter." }

        chapters = runCatching { graph.repository.chapters(sourceId,novelUrl) }.getOrDefault(emptyList()).sortedBy { it.index }
        downloaded = withContext(Dispatchers.IO) { graph.repository.db().downloadedUrls(sourceId,novelUrl) }
        withContext(Dispatchers.IO) { graph.repository.db().recordHistory(sourceId,novelUrl,chapterUrl,index,title) }

        val pos = chapters.indexOfFirst { it.url==chapterUrl }.takeIf { it>=0 } ?: index
        listOfNotNull(chapters.getOrNull(pos-1),chapters.getOrNull(pos+1)).forEach { ref ->
            scope.launch(Dispatchers.IO) { graph.repository.prefetch(ref) }
        }
    }

    val paragraphs = remember(content?.plainText) {
        content?.plainText?.split(Regex("\\n\\s*\\n"))?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    }

    val scrollProgress by remember {
        derivedStateOf {
            val total=(paragraphs.size+1).coerceAtLeast(1)
            (listState.firstVisibleItemIndex.toFloat()/total.toFloat()).coerceIn(0f,1f)
        }
    }

    val pages = remember(paragraphs) { paragraphs.chunked(4).ifEmpty { listOf(emptyList()) } }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val chapterProgress = if (prefs.mode=="PAGED") {
        if (pages.size<=1) 0f else (pagerState.currentPage.toFloat()/(pages.size-1).toFloat()).coerceIn(0f,1f)
    } else scrollProgress
    val bookProgress = if (chapters.isEmpty()) chapterProgress else {
        val position=chapters.indexOfFirst { it.url==chapterUrl }.takeIf { it>=0 } ?: index
        ((position.toFloat()+chapterProgress)/chapters.size.toFloat()).coerceIn(0f,1f)
    }

    suspend fun saveProgressNow() {
        val paragraph = if (prefs.mode=="PAGED") (pagerState.currentPage*4).coerceAtMost(paragraphs.lastIndex.coerceAtLeast(0)) else (listState.firstVisibleItemIndex-1).coerceAtLeast(0)
        val offset = if (prefs.mode=="SCROLL") listState.firstVisibleItemScrollOffset else 0
        withContext(Dispatchers.IO) {
            graph.repository.db().saveProgress(sourceId,novelUrl,chapterUrl,index,chapterProgress,paragraph,offset)
        }
    }

    LaunchedEffect(content?.chapterUrl, paragraphs.size, prefs.mode) {
        val loaded=content ?: return@LaunchedEffect
        if (paragraphs.isEmpty()) return@LaunchedEffect
        val saved=withContext(Dispatchers.IO) { graph.repository.db().getReadingProgress(sourceId,novelUrl) }
        if(saved?.chapterUrl==loaded.chapterUrl) {
            if(prefs.mode=="PAGED") pagerState.scrollToPage((saved.paragraphIndex/4).coerceIn(0,pages.lastIndex))
            else listState.scrollToItem((saved.paragraphIndex+1).coerceAtMost(paragraphs.size),saved.scrollOffset)
        }
    }

    LaunchedEffect(chapterUrl,prefs.mode) {
        if(content==null) return@LaunchedEffect
        if(prefs.mode=="PAGED") {
            snapshotFlow { pagerState.currentPage }.collectLatest { delay(400); saveProgressNow() }
        } else {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collectLatest { delay(400); saveProgressNow() }
        }
    }

    DisposableEffect(lifecycleOwner, chapterUrl) {
        val observer=LifecycleEventObserver { _, event ->
            if(event==Lifecycle.Event.ON_STOP || event==Lifecycle.Event.ON_PAUSE) scope.launch { saveProgressNow() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.launch { saveProgressNow() }
            scope.launch(Dispatchers.IO) { graph.repository.db().addReadingSeconds(((System.currentTimeMillis()-sessionStarted)/1000).coerceAtLeast(1)) }
        }
    }

    DisposableEffect(prefs.immersive,prefs.keepAwake,prefs.brightness,prefs.orientation) {
        val window=activity?.window
        if(window!=null) {
            view.keepScreenOn=prefs.keepAwake
            window.attributes = window.attributes.apply { screenBrightness = if(prefs.brightness<0f) -1f else prefs.brightness.coerceIn(0.05f,1f) }
            val controller=WindowCompat.getInsetsController(window,view)
            if(prefs.immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else controller.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = when(prefs.orientation) {
                "PORTRAIT" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "LANDSCAPE" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            view.keepScreenOn=false
            activity?.window?.let { WindowCompat.getInsetsController(it,view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    val currentPosition=chapters.indexOfFirst { it.url==chapterUrl }.takeIf { it>=0 } ?: index
    val previous=chapters.getOrNull(currentPosition-1)
    val next=chapters.getOrNull(currentPosition+1)

    DisposableEffect(prefs.volumeNavigation, previous?.url, next?.url, prefs.mode) {
        ReaderKeyRouter.enabled=prefs.volumeNavigation
        ReaderKeyRouter.onUp={
            if(prefs.mode=="PAGED") scope.launch { pagerState.animateScrollToPage((pagerState.currentPage-1).coerceAtLeast(0)) }
            else scope.launch { listState.animateScrollBy(-700f) }
        }
        ReaderKeyRouter.onDown={
            if(prefs.mode=="PAGED") scope.launch { pagerState.animateScrollToPage((pagerState.currentPage+1).coerceAtMost(pages.lastIndex)) }
            else scope.launch { listState.animateScrollBy(700f) }
        }
        onDispose { ReaderKeyRouter.clear() }
    }

    val currentParagraph = if(prefs.mode=="PAGED") (pagerState.currentPage*4).coerceAtMost(paragraphs.lastIndex.coerceAtLeast(0)) else (listState.firstVisibleItemIndex-1).coerceAtLeast(0)
    var bookmarked by remember(chapterUrl,currentParagraph) { mutableStateOf(false) }
    LaunchedEffect(chapterUrl,currentParagraph) {
        bookmarked=withContext(Dispatchers.IO) { graph.repository.db().isBookmarked(sourceId,chapterUrl,currentParagraph) }
    }

    fun navigate(ref: ChapterRef) {
        scope.launch { saveProgressNow(); onNavigateChapter(ref) }
    }

    ModalNavigationDrawer(
        drawerState=drawerState,
        gesturesEnabled=true,
        drawerContent={
            ModalDrawerSheet(modifier=Modifier.width(330.dp)) {
                Text("Chapters",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(18.dp))
                OutlinedTextField(
                    value=drawerQuery,onValueChange={drawerQuery=it},
                    placeholder={Text("Find chapter")},
                    modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("${chapters.size} chapters",modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(enabled=!refreshingChapters,onClick={ scope.launch {
                        refreshingChapters=true
                        runCatching { graph.repository.refreshChapters(sourceId,novelUrl) }.onSuccess { (fresh,count) -> chapters=fresh.sortedBy { it.index }; newChapterCount=count }
                        refreshingChapters=false
                    }}) { if(refreshingChapters) CircularProgressIndicator(modifier=Modifier.padding(10.dp)) else Icon(Icons.Rounded.Refresh,"Refresh") }
                }
                val shown=chapters.filter { drawerQuery.isBlank() || it.title.contains(drawerQuery,true) || (it.index+1).toString()==drawerQuery.trim() }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(shown,key={_,it->it.url}) { _, ch ->
                        Row(
                            Modifier.fillMaxWidth().background(if(ch.url==chapterUrl) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .combinedClickable(onClick={scope.launch { drawerState.close(); navigate(ch) }})
                                .padding(horizontal=16.dp,vertical=12.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ) {
                            Text("${ch.index+1}",color=MaterialTheme.colorScheme.primary,modifier=Modifier.width(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.title,maxLines=2,overflow=TextOverflow.Ellipsis)
                                if(ch.url in downloaded) Text("Downloaded",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(colors.bg)) {
            if(showChrome) {
                Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={scope.launch { saveProgressNow(); onBack() }}) { Icon(Icons.Rounded.ArrowBack,"Back",tint=colors.fg) }
                    Column(Modifier.weight(1f)) {
                        Text(title,color=colors.fg,fontWeight=FontWeight.SemiBold,maxLines=1)
                        Text("Chapter ${index+1} · ${(chapterProgress*100).roundToInt()}% · ${(bookProgress*100).roundToInt()}% book",color=colors.muted,style=MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick={scope.launch { drawerState.open() }}) { Icon(Icons.Rounded.FormatListBulleted,"Chapters",tint=colors.fg) }
                    IconButton(onClick={showFind=true}) { Icon(Icons.Rounded.FindInPage,"Find",tint=colors.fg) }
                    IconButton(onClick={scope.launch(Dispatchers.IO) {
                        val now=graph.repository.db().toggleBookmark(sourceId,novelUrl,chapterUrl,index,currentParagraph,if(prefs.mode=="SCROLL") listState.firstVisibleItemScrollOffset else 0,title)
                        withContext(Dispatchers.Main) { bookmarked=now }
                    }}) { Icon(if(bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,"Bookmark",tint=colors.fg) }
                    IconButton(onClick={showSettings=true}) { Icon(Icons.Rounded.Settings,"Reader settings",tint=colors.fg) }
                }
                LinearProgressIndicator(progress={chapterProgress},modifier=Modifier.fillMaxWidth())
                if(newChapterCount>0) Text("+$newChapterCount new chapter${if(newChapterCount==1) "" else "s"} available",color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
            }

            when {
                content==null && error==null -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
                error!=null -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
                        Button(onClick={scope.launch {
                            error=null
                            val ref=ChapterRef(sourceId,novelUrl,title,chapterUrl,index)
                            runCatching { graph.repository.chapterLoad(ref) }.onSuccess { content=it.content; origin=it.origin }.onFailure { error=it.message }
                        }},modifier=Modifier.padding(top=12.dp)) { Text("Retry") }
                        Text("Source: $sourceId",color=colors.muted,modifier=Modifier.padding(top=8.dp))
                    }
                }
                prefs.mode=="PAGED" -> HorizontalPager(
                    state=pagerState,
                    modifier=Modifier.weight(1f).fillMaxWidth().combinedClickable(onClick={showChrome=!showChrome})
                ) { page ->
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal=prefs.margin.dp)) {
                        if(page==0) item { Text(content!!.title,color=colors.fg,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=(prefs.fontSize+5).sp,modifier=Modifier.padding(vertical=24.dp)) }
                        itemsIndexed(pages[page]) { _, paragraph ->
                            Text(paragraph,color=colors.fg,fontFamily=FontFamily.Serif,fontSize=prefs.fontSize.sp,lineHeight=(prefs.fontSize*prefs.lineHeight).sp,modifier=Modifier.padding(vertical=prefs.paragraphSpacing.dp))
                        }
                    }
                }
                else -> LazyColumn(
                    state=listState,
                    modifier=Modifier.weight(1f).fillMaxWidth().combinedClickable(onClick={showChrome=!showChrome})
                ) {
                    item {
                        Text(content!!.title,color=colors.fg,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=(prefs.fontSize+5).sp,lineHeight=(prefs.fontSize+12).sp,modifier=Modifier.padding(horizontal=prefs.margin.dp,vertical=24.dp))
                    }
                    itemsIndexed(paragraphs,key={i,_->i}) { _, paragraph ->
                        Text(paragraph,color=colors.fg,fontFamily=FontFamily.Serif,fontSize=prefs.fontSize.sp,lineHeight=(prefs.fontSize*prefs.lineHeight).sp,modifier=Modifier.padding(horizontal=prefs.margin.dp,vertical=prefs.paragraphSpacing.dp))
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text("End of chapter",color=colors.muted)
                            Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(onClick={previous?.let(::navigate)},enabled=previous!=null,modifier=Modifier.weight(1f)) { Icon(Icons.Rounded.ArrowBack,null); Text(" Previous") }
                                FilledTonalButton(onClick={next?.let(::navigate)},enabled=next!=null,modifier=Modifier.weight(1f)) { Text("Next "); Icon(Icons.Rounded.ArrowForward,null) }
                            }
                            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                TextButton(onClick={enqueueRange(context,ChapterRef(sourceId,novelUrl,title,chapterUrl,index),index,(index+9).coerceAtMost(chapters.lastOrNull()?.index ?: index))}) { Icon(Icons.Rounded.Download,null); Text(" Next 10") }
                                TextButton(onClick={onBack}) { Icon(Icons.Rounded.MenuBook,null); Text(" Book") }
                            }
                        }
                    }
                }
            }

            if(showChrome && content!=null && prefs.mode=="PAGED") {
                Row(Modifier.fillMaxWidth().background(colors.bg).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick={previous?.let(::navigate)},enabled=previous!=null,modifier=Modifier.weight(1f)) { Text("Previous") }
                    FilledTonalButton(onClick={next?.let(::navigate)},enabled=next!=null,modifier=Modifier.weight(1f)) { Text("Next") }
                }
            }

            if(showChrome && content!=null) {
                Text("$origin · ${chapters.size} chapters",color=colors.muted,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp))
            }
        }
    }

    if(showSettings) {
        ModalBottomSheet(onDismissRequest={showSettings=false}) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp)) {
                Text("Reader settings",style=MaterialTheme.typography.headlineMedium)
                Text("Theme",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=14.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("AMOLED","DARK","SEPIA","PAPER","WHITE").forEach { name ->
                        TextButton(onClick={scope.launch { graph.readerPreferences.update { it.copy(theme=name) } }}) { Text(name) }
                    }
                }
                Text("Reading mode",style=MaterialTheme.typography.titleMedium)
                Row { listOf("SCROLL","PAGED").forEach { m -> TextButton(onClick={scope.launch { graph.readerPreferences.update { it.copy(mode=m) } }}) { Text(m) } } }
                Text("Font size ${prefs.fontSize.roundToInt()}")
                Slider(value=prefs.fontSize,onValueChange={v->scope.launch { graph.readerPreferences.update { it.copy(fontSize=v) } }},valueRange=14f..30f)
                Text("Line height ${"%.2f".format(prefs.lineHeight)}")
                Slider(value=prefs.lineHeight,onValueChange={v->scope.launch { graph.readerPreferences.update { it.copy(lineHeight=v) } }},valueRange=1.2f..2.2f)
                Text("Paragraph spacing ${prefs.paragraphSpacing.roundToInt()}dp")
                Slider(value=prefs.paragraphSpacing,onValueChange={v->scope.launch { graph.readerPreferences.update { it.copy(paragraphSpacing=v) } }},valueRange=2f..20f)
                Text("Side margins ${prefs.margin.roundToInt()}dp")
                Slider(value=prefs.margin,onValueChange={v->scope.launch { graph.readerPreferences.update { it.copy(margin=v) } }},valueRange=10f..42f)
                Text("Brightness ${if(prefs.brightness<0f) "System" else "${(prefs.brightness*100).roundToInt()}%"}")
                Slider(value=if(prefs.brightness<0f) 0.5f else prefs.brightness,onValueChange={v->scope.launch { graph.readerPreferences.update { it.copy(brightness=v) } }},valueRange=0.05f..1f)
                TextButton(onClick={scope.launch { graph.readerPreferences.update { it.copy(brightness=-1f) } }}) { Text("Use system brightness") }
                SettingSwitch("Immersive fullscreen",prefs.immersive) { scope.launch { graph.readerPreferences.update { p->p.copy(immersive=it) } } }
                SettingSwitch("Keep screen awake",prefs.keepAwake) { scope.launch { graph.readerPreferences.update { p->p.copy(keepAwake=it) } } }
                SettingSwitch("Volume buttons scroll/page",prefs.volumeNavigation) { scope.launch { graph.readerPreferences.update { p->p.copy(volumeNavigation=it) } } }
                Text("Orientation",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp))
                Row { listOf("AUTO","PORTRAIT","LANDSCAPE").forEach { o -> TextButton(onClick={scope.launch { graph.readerPreferences.update { it.copy(orientation=o) } }}) { Text(o) } } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if(showFind) {
        AlertDialog(
            onDismissRequest={showFind=false},
            title={Text("Find in chapter")},
            text={
                Column {
                    OutlinedTextField(value=findQuery,onValueChange={findQuery=it},label={Text("Word or phrase")})
                    val matches=remember(findQuery,paragraphs) { if(findQuery.isBlank()) emptyList() else paragraphs.mapIndexedNotNull { i,p->i.takeIf { p.contains(findQuery,true) } } }
                    Text("${matches.size} matches",modifier=Modifier.padding(top=10.dp))
                    matches.take(8).forEach { i ->
                        TextButton(onClick={scope.launch { if(prefs.mode=="PAGED") pagerState.animateScrollToPage((i/4).coerceAtMost(pages.lastIndex)) else listState.animateScrollToItem(i+1); showFind=false }}) { Text("Paragraph ${i+1}: ${paragraphs[i].take(80)}") }
                    }
                }
            },
            confirmButton={TextButton(onClick={showFind=false}) { Text("Close") }}
        )
    }
}

@Composable
private fun SettingSwitch(label: String,checked: Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,modifier=Modifier.weight(1f))
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
