package com.example.radioku

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextField
import android.os.Bundle
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class RadioStation(
    val name: String,
    val streamUrl: String
)

class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null

    private var isPlaying by mutableStateOf(false)
        // ==============================
    // INTERSTITIAL AD
    // ==============================

    private var interstitialAd: InterstitialAd? = null

    // Total waktu radio benar-benar sedang diputar
    private var playedMillis: Long = 0L

    // Waktu ketika sesi PLAY dimulai
    private var playStartTime: Long = 0L

    // Apakah sudah mencapai 5 menit
    private var interstitialReady = false

    // 5 menit dalam milidetik
    private val interstitialInterval = 5 * 60 * 1000L
    private var selectedRadio by mutableStateOf(
        RadioStation(
            "Memuat radio...",
            ""
        )
    )

    private var radioStations by mutableStateOf(
        listOf<RadioStation>()
    )

    private var isLoading by mutableStateOf(true)

    private var errorMessage by mutableStateOf("")
    private var searchText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this)
        loadInterstitialAd()
        val sessionToken = SessionToken(
            this,
            android.content.ComponentName(
                this,
                PlaybackService::class.java
            )
        )

        val controllerFuture =
            MediaController.Builder(this, sessionToken)
                .buildAsync()

        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()

                isPlaying =
                    mediaController?.isPlaying == true
            },
            MoreExecutors.directExecutor()
        )

        setContent {

            RadioKuApp(
                radioStations = radioStations,
                selectedRadio = selectedRadio,
                isPlaying = isPlaying,
                isLoading = isLoading,
                errorMessage = errorMessage,
                searchText = searchText,
                onSearchTextChange = {
                      searchText = it
                },

                onRadioSelected = { radio ->
                    selectedRadio = radio
                    playRadio(radio)
                },

                onPlayPause = {
                    togglePlayback()
                }
            )
        }

        loadIndonesianRadios()
    }
    private fun loadInterstitialAd() {

    val adRequest =
        AdRequest.Builder().build()

    InterstitialAd.load(
        this,
        "ca-app-pub-3940256099942544/1033173712",
        adRequest,
        object : InterstitialAdLoadCallback() {

            override fun onAdLoaded(
                ad: InterstitialAd
            ) {

                interstitialAd = ad
            }

            override fun onAdFailedToLoad(
                adError: com.google.android.gms.ads.LoadAdError
            ) {

                interstitialAd = null
            }
        }
    )
}
    private fun loadIndonesianRadios() {

        Thread {

            try {

                val url = URL(
                    "https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/ID?hidebroken=true&limit=1000"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"

                connection.connectTimeout = 10000
                connection.readTimeout = 15000

                connection.setRequestProperty(
                    "User-Agent",
                    "RadioKu/1.0 Android"
                )

                val responseCode =
                    connection.responseCode

                if (responseCode != HttpURLConnection.HTTP_OK) {

                    throw Exception(
                        "Server mengembalikan kode $responseCode"
                    )
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                connection.disconnect()

                val jsonArray =
                    JSONArray(response)

                val stations =
                    mutableListOf<RadioStation>()

                for (i in 0 until jsonArray.length()) {

                    val station =
                        jsonArray.getJSONObject(i)

                    val name =
                        station.optString("name")

                    val streamUrl =
                        station.optString("url_resolved")
                           .ifEmpty {
                               station.optString("url")
                           }

                    val lastCheckOk =
                        station.optInt("lastcheckok", 0)

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

                runOnUiThread {

                    if (stations.isNotEmpty()) {

                        radioStations = stations

                        selectedRadio =
                            stations.first()

                        isLoading = false
                        errorMessage = ""

                    } else {

                        isLoading = false

                        errorMessage =
                            "Tidak ada stasiun radio Indonesia yang ditemukan."
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    isLoading = false

                    errorMessage =
                        "Gagal mengambil daftar radio: ${e.message}"
                }
            }

        }.start()
    }

    private fun playRadio(radio: RadioStation) {

    val controller =
        mediaController ?: return

    if (radio.streamUrl.isBlank()) {
        return
    }

    val mediaItem =
        MediaItem.Builder()
            .setUri(radio.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(radio.name)
                    .setArtist("RadioKu")
                    .build()
            )
            .build()

    controller.setMediaItem(mediaItem)

    controller.prepare()

    controller.play()

    isPlaying = true

    // Mulai menghitung waktu radio benar-benar diputar
    playStartTime =
        System.currentTimeMillis()
}

        val mediaItem =
            MediaItem.Builder()
                .setUri(radio.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(radio.name)
                        .setArtist("RadioKu")
                        .build()
                )
                .build()

        controller.setMediaItem(mediaItem)

        controller.prepare()

        controller.play()

        isPlaying = true
    }

    private fun togglePlayback() {

    val controller =
        mediaController ?: return

    // ==============================
    // JIKA SEDANG PLAY → PAUSE
    // ==============================

    if (controller.isPlaying) {

        // Hitung waktu yang sudah benar-benar diputar
        playedMillis +=
            System.currentTimeMillis() - playStartTime

        controller.pause()

        isPlaying = false

        // Cek apakah sudah mencapai 5 menit
        if (playedMillis >= interstitialInterval) {
            interstitialReady = true
        }

        return
    }

    // ==============================
    // JIKA SEDANG PAUSE → PLAY
    // ==============================

    // Jika belum mencapai 5 menit,
    // langsung lanjutkan radio
    if (!interstitialReady) {

        controller.play()

        playStartTime =
            System.currentTimeMillis()

        isPlaying = true

        return
    }

    // ==============================
    // SUDAH 5 MENIT → SIAPKAN IKLAN
    // ==============================

    val ad = interstitialAd

    // Jika iklan belum berhasil dimuat,
    // jangan mengganggu pemutaran radio
    if (ad == null) {

        controller.play()

        playStartTime =
            System.currentTimeMillis()

        isPlaying = true

        return
    }

    // ==============================
    // TAMPILKAN INTERSTITIAL
    // ==============================

    ad.fullScreenContentCallback =
    object :
        com.google.android.gms.ads.FullScreenContentCallback() {

        override fun onAdDismissedFullScreenContent() {

            playedMillis = 0L
            interstitialReady = false

            loadInterstitialAd()

            controller.play()

            playStartTime =
                System.currentTimeMillis()

            isPlaying = true
        }

        override fun onAdFailedToShowFullScreenContent(
            adError: com.google.android.gms.ads.AdError
        ) {

            loadInterstitialAd()

            controller.play()

            playStartTime =
                System.currentTimeMillis()

            isPlaying = true
        }
    }

interstitialAd = null

ad.show(this)

    // Setelah iklan selesai,
    // radio akan dilanjutkan
    

            override fun onAdDismissedFullScreenContent() {

                playedMillis = 0L
                interstitialReady = false

                loadInterstitialAd()

                controller.play()

                playStartTime =
                    System.currentTimeMillis()

                isPlaying = true
            }

            override fun onAdFailedToShowFullScreenContent(
                adError: com.google.android.gms.ads.AdError
            ) {

                loadInterstitialAd()

                controller.play()

                playStartTime =
                    System.currentTimeMillis()

                isPlaying = true
            }
        }
}

    override fun onDestroy() {

        mediaController?.release()

        mediaController = null

        super.onDestroy()
    }
}

@Composable
fun RadioKuApp(
    radioStations: List<RadioStation>,
    selectedRadio: RadioStation,
    isPlaying: Boolean,
    isLoading: Boolean,
    errorMessage: String,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onRadioSelected: (RadioStation) -> Unit,
    onPlayPause: () -> Unit
)
{

    MaterialTheme {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            
                    

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "📻",
                        fontSize = 48.sp
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = selectedRadio.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = if (isPlaying) {
                            "● Sedang Mengudara"
                        } else {
                            "Siap diputar"
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Button(
                        onClick = onPlayPause,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                Color(0xFF2E7D32),
                            contentColor =
                                Color.White
                        )
                    ) {

                        Text(
                            text = if (isPlaying) {
                                "⏸ PAUSE"
                            } else {
                                "▶ PLAY"
                            },
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedTextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Cari radio")
                },
                placeholder = {
                    Text("Ketik nama radio...")
                },
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            if (isLoading) {

                CircularProgressIndicator()

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "Memuat daftar radio Indonesia..."
                )

            } else if (errorMessage.isNotEmpty()) {

                Text(
                    text = errorMessage
                )

            } else {

                val filteredRadioStations =
                    radioStations.filter { radio ->
                        radio.name.contains(
                            searchText,
                            ignoreCase = true
                        )
                    }

                LazyColumn(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
) {

    items(filteredRadioStations) { radio ->

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onRadioSelected(radio)
                }
                .padding(
                    vertical = 12.dp,
                    horizontal = 8.dp
                )
        ) {

            Text(
                text = radio.name,
                fontSize = 16.sp,
                fontWeight =
                    if (radio == selectedRadio) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                color =
                    if (radio == selectedRadio) {
                        Color(0xFF2E7D32)
                    } else {
                        Color.Unspecified
                    }
            )

            HorizontalDivider()
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
}

@Composable
fun BannerAdView() {

    val context = LocalContext.current

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(),
        factory = {

            AdView(context).apply {

                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        360
                    )
                )

                adUnitId =
                    "ca-app-pub-3940256099942544/9214589741"

                loadAd(
                    AdRequest.Builder().build()
                )
            }
        }
    )
}
