package com.example.radioku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import com.example.radioku.ui.theme.RadioKuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

data class RadioStation(
    val name: String,
    val streamUrl: String
)

private const val RADIO_BROWSER_URL =
    "https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/ID?hidebroken=true&limit=1000"

private const val PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.example.radioku"

private const val PRIVACY_POLICY_URL =
    "https://MASUKKAN-URL-PRIVACY-POLICY-ANDA-DI-SINI"

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null

    private var interstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)

        createMediaController()
        loadInterstitialAd()

        setContent {
            RadioKuTheme {
                RadioKuApp(
                    onPlayRadio = { radio ->
                        playRadio(radio)
                    },
                    onPauseRadio = {
                        pauseRadio()
                    }
                )
            }
        }
    }

    private fun createMediaController() {
        val sessionToken = SessionToken(
            this,
            android.content.ComponentName(
                this,
                PlaybackService::class.java
            )
        )

        val controllerFuture = MediaController.Builder(
            this,
            sessionToken
        ).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    mediaController = controllerFuture.get()
                } catch (_: Exception) {
                }
            },
            mainExecutor
        )
    }

    private fun playRadio(radio: RadioStation) {
        val controller = mediaController ?: return

        val mediaItem = MediaItem.fromUri(radio.streamUrl)

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        showInterstitialAd()
    }

    private fun pauseRadio() {
        mediaController?.pause()
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun showInterstitialAd() {
        val ad = interstitialAd ?: return

        ad.show(this)

        interstitialAd = null

        loadInterstitialAd()
    }

    override fun onDestroy() {
        mediaController?.release()
        mediaController = null

        super.onDestroy()
    }
}

@Composable
fun RadioKuApp(
    onPlayRadio: (RadioStation) -> Unit,
    onPauseRadio: () -> Unit
) {
    val context = LocalContext.current

    val radioStations = remember {
        mutableStateListOf<RadioStation>()
    }

    var selectedRadio by remember {
        mutableStateOf<RadioStation?>(null)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var showMenu by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        try {
            val result = withContext(Dispatchers.IO) {
                val connection = URL(RADIO_BROWSER_URL).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 15000

                val text = connection.getInputStream()
                    .bufferedReader()
                    .use { it.readText() }

                val jsonArray = JSONArray(text)

                val stations = mutableListOf<RadioStation>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)

                    val name = item.optString("name")
                    val streamUrl = item.optString("url_resolved")
                        .ifBlank {
                            item.optString("url")
                        }

                    val lastCheckOk = item.optInt(
                        "lastcheckok",
                        0
                    )

                    if (
                        name.isNotBlank() &&
                        streamUrl.isNotBlank() &&
                        lastCheckOk == 1
                    ) {
                        stations.add(
                            RadioStation(
                                name = name,
                                streamUrl = streamUrl
                            )
                        )
                    }
                }

                stations
            }

            radioStations.clear()
            radioStations.addAll(result)

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Gagal mengambil daftar radio",
                Toast.LENGTH_LONG
            ).show()
        }

        isLoading = false
    }

    val filteredStations = radioStations.filter {
        it.name.contains(
            searchText,
            ignoreCase = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {

            Text(
                text = "RadioKu",
                modifier = Modifier.align(Alignment.CenterStart),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
            ) {

                IconButton(
                    onClick = {
                        showMenu = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = {
                        showMenu = false
                    },
                    offset = DpOffset(
                        x = (-80).dp,
                        y = (-8).dp
                    )
                ) {

                    DropdownMenuItem(
                        text = {
                            Text("Rate Us")
                        },
                        onClick = {
                            showMenu = false

                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(PLAY_STORE_URL)
                                    )
                                )
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Tidak dapat membuka Play Store",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Privacy Policy")
                        },
                        onClick = {
                            showMenu = false

                            if (
                                PRIVACY_POLICY_URL.startsWith("http")
                            ) {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(PRIVACY_POLICY_URL)
                                        )
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Tidak dapat membuka halaman Privacy Policy",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "URL Privacy Policy belum diisi",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                if (selectedRadio != null) {

                    Text(
                        text = selectedRadio!!.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Button(
                        onClick = {
                            if (isPlaying) {
                                onPauseRadio()
                                isPlaying = false
                            } else {
                                selectedRadio?.let {
                                    onPlayRadio(it)
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors()
                    ) {

                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )

                        Text(
                            text = if (isPlaying) {
                                "Pause"
                            } else {
                                "Play"
                            }
                        )
                    }

                } else {

                    Text(
                        text = "Pilih radio untuk mulai mendengarkan",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(24.dp)
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                BasicTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->

                        if (searchText.isEmpty()) {
                            Text(
                                text = "Cari nama radio...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        innerTextField()
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        HorizontalDivider()

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        if (isLoading) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {

                CircularProgressIndicator()
            }

        } else if (filteredStations.isEmpty()) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "No streaming radio found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                items(
                    items = filteredStations,
                    key = {
                        it.streamUrl
                    }
                ) { radio ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {

                                selectedRadio = radio

                                onPlayRadio(radio)

                                isPlaying = true
                            },
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(
                                modifier = Modifier.width(12.dp)
                            )

                            Text(
                                text = radio.name,
                                modifier = Modifier.weight(1f),
                                fontSize = 16.sp,
                                fontWeight = if (
                                    selectedRadio == radio
                                ) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        BannerAdView()
    }
}

@Composable
fun BannerAdView() {

    val context = LocalContext.current

    AndroidView(
        factory = {
            AdView(context).apply {

                setAdSize(
                    AdSize.BANNER
                )

                adUnitId =
                    "ca-app-pub-3940256099942544/6300978111"

                loadAd(
                    AdRequest.Builder().build()
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    )
}
