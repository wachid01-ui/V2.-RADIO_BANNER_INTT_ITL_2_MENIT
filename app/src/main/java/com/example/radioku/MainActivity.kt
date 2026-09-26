```kotlin
package com.example.radioku

import android.content.Intent
import android.net.Uri
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

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


private const val PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.example.radioku"


private const val PRIVACY_POLICY_URL =
    "https://MASUKKAN-URL-PRIVACY-POLICY-ANDA-DI-SINI"


class MainActivity : ComponentActivity() {

    private var mediaController: MediaController? = null

    private var isPlaying by mutableStateOf(false)


    // ============================================================
    // INTERSTITIAL AD
    // ============================================================

    private var interstitialAd: InterstitialAd? = null

    // Total waktu radio benar-benar sedang diputar
    private var playedMillis: Long = 0L

    // Waktu ketika sesi PLAY dimulai
    private var playStartTime: Long = 0L

    // Apakah sudah mencapai batas waktu interstitial
    private var interstitialReady = false


    // ============================================================
    // UNTUK TESTING = 10 DETIK
    // Nanti setelah selesai testing ubah menjadi:
    //
    // private val interstitialInterval = 2 * 60 * 1000L
    // ============================================================

    private val interstitialInterval =
        10 * 1000L


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


    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)


        // ========================================================
        // INITIALIZE ADMOB
        // ========================================================

        MobileAds.initialize(this)

        loadInterstitialAd()


        // ========================================================
        // MEDIA CONTROLLER
        // ========================================================

        val sessionToken =
            SessionToken(
                this,
                android.content.ComponentName(
                    this,
                    PlaybackService::class.java
                )
            )


        val controllerFuture =
            MediaController.Builder(
                this,
                sessionToken
            ).buildAsync()


        controllerFuture.addListener(

            {

                mediaController =
                    controllerFuture.get()

                isPlaying =
                    mediaController?.isPlaying == true
            },

            MoreExecutors.directExecutor()
        )


        // ========================================================
        // COMPOSE UI
        // ========================================================

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


        // ========================================================
        // LOAD RADIO INDONESIA
        // ========================================================

        loadIndonesianRadios()
    }


    // ============================================================
    // LOAD INTERSTITIAL AD
    // ============================================================

    private fun loadInterstitialAd() {

        val adRequest =
            AdRequest.Builder().build()


        InterstitialAd.load(

            this,

            "ca-app-pub-3940256099942544/1033173712",

            adRequest,


            object :
                InterstitialAdLoadCallback() {


                override fun onAdLoaded(
                    ad: InterstitialAd
                ) {

                    interstitialAd = ad
                }


                override fun onAdFailedToLoad(
                    adError:
                        com.google.android.gms.ads.LoadAdError
                ) {

                    interstitialAd = null
                }
            }
        )
    }


    // ============================================================
    // LOAD RADIO INDONESIA
    // ============================================================

    private fun loadIndonesianRadios() {

        Thread {

            try {

                val url =
                    URL(
                        "https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/ID?hidebroken=true&limit=1000"
                    )


                val connection =
                    url.openConnection()
                        as HttpURLConnection


                connection.requestMethod =
                    "GET"


                connection.connectTimeout =
                    10000


                connection.readTimeout =
                    15000


                connection.setRequestProperty(
                    "User-Agent",
                    "RadioKu/1.0 Android"
                )


                val responseCode =
                    connection.responseCode


                if (
                    responseCode !=
                    HttpURLConnection.HTTP_OK
                ) {

                    throw Exception(
                        "Server mengembalikan kode $responseCode"
                    )
                }


                val response =
                    connection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }


                connection.disconnect()


                val jsonArray =
                    JSONArray(response)


                val stations =
                    mutableListOf<RadioStation>()


                for (
                    i in 0 until jsonArray.length()
                ) {

                    val station =
                        jsonArray
                            .getJSONObject(i)


                    val name =
                        station.optString(
                            "name"
                        )


                    val streamUrl =
                        station
                            .optString(
                                "url_resolved"
                            )
                            .ifEmpty {

                                station.optString(
                                    "url"
                                )
                            }


                    val lastCheckOk =
                        station.optInt(
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

                                streamUrl =
                                    streamUrl
                            )
                        )
                    }
                }


                runOnUiThread {

                    if (
                        stations.isNotEmpty()
                    ) {

                        radioStations =
                            stations


                        selectedRadio =
                            stations.first()


                        isLoading =
                            false


                        errorMessage =
                            ""

                    } else {

                        isLoading =
                            false


                        errorMessage =
                            "Tidak ada stasiun radio Indonesia yang ditemukan."
                    }
                }

            } catch (
                e: Exception
            ) {

                runOnUiThread {

                    isLoading =
                        false


                    errorMessage =
                        "Gagal mengambil daftar radio: ${e.message}"
                }
            }

        }.start()
    }


    // ============================================================
    // PLAY RADIO
    // ============================================================

    private fun playRadio(
        radio: RadioStation
    ) {

        val controller =
            mediaController
                ?: return


        if (
            radio.streamUrl.isBlank()
        ) {

            return
        }


        // Jika sebelumnya belum sedang play,
        // mulai menghitung waktu dari sekarang.

        if (
            !controller.isPlaying
        ) {

            playStartTime =
                System.currentTimeMillis()
        }


        val mediaItem =
            MediaItem.Builder()

                .setUri(
                    radio.streamUrl
                )

                .setMediaMetadata(

                    MediaMetadata.Builder()

                        .setTitle(
                            radio.name
                        )

                        .setArtist(
                            "RadioKu"
                        )

                        .build()
                )

                .build()


        controller.setMediaItem(
            mediaItem
        )


        controller.prepare()


        controller.play()


        isPlaying = true
    }


    // ============================================================
    // PLAY / PAUSE
    // ============================================================

    private fun togglePlayback() {

        val controller =
            mediaController
                ?: return


        // ========================================================
        // JIKA SEDANG PLAY → PAUSE
        // ========================================================

        if (
            controller.isPlaying
        ) {

            if (
                playStartTime > 0L
            ) {

                playedMillis +=
                    System.currentTimeMillis() -
                        playStartTime
            }


            controller.pause()


            isPlaying = false


            playStartTime =
                0L


            if (
                playedMillis >=
                interstitialInterval
            ) {

                interstitialReady =
                    true
            }


            return
        }


        // ========================================================
        // JIKA PAUSE → PLAY
        // ========================================================

        if (
            !interstitialReady
        ) {

            controller.play()


            playStartTime =
                System.currentTimeMillis()


            isPlaying = true


            return
        }


        // ========================================================
        // SUDAH MENCAPAI INTERVAL
        // ========================================================

        val ad =
            interstitialAd


        // ========================================================
        // IKLAN BELUM TERSEDIA
        // ========================================================

        if (
            ad == null
        ) {

            controller.play()


            playStartTime =
                System.currentTimeMillis()


            isPlaying = true


            return
        }


        // ========================================================
        // IKLAN TERSEDIA
        // ========================================================

        ad.fullScreenContentCallback =

            object :
                com.google.android.gms.ads.FullScreenContentCallback() {


                override fun
                    onAdDismissedFullScreenContent() {


                    // RESET TIMER

                    playedMillis =
                        0L


                    interstitialReady =
                        false


                    playStartTime =
                        0L


                    // LOAD IKLAN BERIKUTNYA

                    loadInterstitialAd()


                    // LANJUTKAN RADIO

                    controller.play()


                    playStartTime =
                        System.currentTimeMillis()


                    isPlaying = true
                }


                override fun
                    onAdFailedToShowFullScreenContent(
                        adError:
                            com.google.android.gms.ads.AdError
                    ) {


                    // RESET TIMER

                    playedMillis =
                        0L


                    interstitialReady =
                        false


                    playStartTime =
                        0L


                    // LOAD IKLAN BERIKUTNYA

                    loadInterstitialAd()


                    // LANJUTKAN RADIO

                    controller.play()


                    playStartTime =
                        System.currentTimeMillis()


                    isPlaying = true
                }
            }


        interstitialAd =
            null


        ad.show(this)
    }


    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        mediaController?.release()

        mediaController = null

        super.onDestroy()
    }
}


// =================================================================
// UI RADIOKU
// =================================================================

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
) {

    val context =
        LocalContext.current


    MaterialTheme {

        Column(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Top
        ) {


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            // ====================================================
            // RADIO YANG SEDANG DIPILIH
            // ====================================================

            Card(

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(20.dp)
            ) {


                Box(

                    modifier =
                        Modifier.fillMaxWidth()
                ) {


                    // ==================================================
                    // MENU ⋮
                    // ==================================================

                    var menuExpanded by remember {
                        mutableStateOf(false)
                    }


                    Box(

                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopEnd
                                )
                                .padding(
                                    top = 4.dp,
                                    end = 4.dp
                                )
                    ) {


                        // TOMBOL ⋮

                        IconButton(

                            onClick = {

                                menuExpanded =
                                    true
                            }
                        ) {

                            Text(

                                text = "⋮",

                                fontSize = 28.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }


                        // ==================================================
                        // POPUP MENU
                        // ==================================================

                        DropdownMenu(

                            expanded =
                                menuExpanded,

                            onDismissRequest = {

                                menuExpanded =
                                    false
                            }
                        ) {


                            // RATE US

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "⭐ Rate Us"
                                    )
                                },

                                onClick = {

                                    menuExpanded =
                                        false


                                    val intent =
                                        Intent(

                                            Intent.ACTION_VIEW,

                                            Uri.parse(
```
