package com.mccarty.ritmo.domain.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.mccarty.ritmo.KeyConstants
import com.mccarty.ritmo.MainActivity
import com.mccarty.ritmo.MainActivity.Companion.INTENT_ACTION
import com.mccarty.ritmo.R
import com.mccarty.ritmo.MainActivity.Companion.TICKER_DELAY as TICKER_DELAY
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class PlaybackService: LifecycleService() {
    private val binder = PlaybackBinder()
    private var spotifyAppRemote: SpotifyAppRemote? = null

    private lateinit var backgroundPlayJob: Job
    private lateinit var scope: CoroutineScope

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        SpotifyAppRemote.connect(this, ConnectionParams.Builder(KeyConstants.CLIENT_ID).apply {
            setRedirectUri(MainActivity.REDIRECT_URI)
            showAuthView(true)
        }.build(), object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                appRemote.playerApi.subscribeToPlayerState().setEventCallback {
                    println("PlaybackService ***** onCreate() $it")
                    sendBroadCast(it)
                }
            }

            override fun onFailure(throwable: Throwable) {
                Timber.e(throwable.message)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        println("PlaybackService ***** onStartCommand()")

        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.track_playback_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).also { it.description = getString(R.string.track_playback_channel_description) }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_music_note_24)
                .setContentTitle(playerState.track?.name)
                .setContentText(playerState.track?.album?.name)
                .setSilent(true)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(playerState.track?.artist?.name)
                )
                .build()

            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }


    fun remote() = spotifyAppRemote

    fun currentUri(currentUri: (String) -> Unit) {
        spotifyAppRemote?.let { remote ->
            remote.playerApi.playerState.setResultCallback { playerState ->
                currentUri(playerState.track?.uri.toString())
            }
        }
    }

    /**
     * This job for keeping track of play state while app is playing in background
     */
    fun cancelBackgroundPlayJob() {
        if (this::backgroundPlayJob.isInitialized && backgroundPlayJob.isActive) {
            backgroundPlayJob.cancel()
        }
    }

    fun tracksHasEnded(playlistData: MainActivity.PlaylistData) {
        backgroundPlayJob = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + backgroundPlayJob)

        scope.launch {

            while (isActive) {
                delay(TICKER_DELAY)
                spotifyAppRemote?.let { remote ->
                    remote.playerApi.playerState?.setResultCallback { playerState ->
                        if (playerState.playbackPosition.milliseconds.inWholeSeconds == (playerState.track?.duration?.milliseconds?.inWholeSeconds?.minus(1L))) {
                            val index = playlistData.playlist.indexOfFirst { it.uri == playerState.track?.uri } + 1
                            if (playlistData.playlist.size == 1) {
                                remote.playerApi.play(null)
                            } else if (playerState.track?.uri ==  playlistData.playlist[playlistData.playlist.size - 1].track?.uri.toString()) {
                                remote.playerApi.play(playlistData.playlist[0].track?.uri)
                            } else {
                                remote.playerApi.play(playlistData.playlist[index].track?.uri)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * All of the data needed to setup track information
     * from the Spotify API
     */
    fun sendBroadCast(playerState: PlayerState) {
        Intent().also { intent ->
            intent.setAction(INTENT_ACTION)
            intent.putExtra(
                PLAYER_STATE,
                Player(
                    imageUri = playerState.track?.imageUri.toString(),
                    trackArtist = playerState.track?.artist?.name,
                    albumName = playerState.track?.album?.name,
                    trackName = playerState.track?.name,
                    trackUri = playerState.track.uri,
                    isTrackPaused = playerState.isPaused,
                    position = playerState.playbackPosition,
                    duration = playerState.track.duration,
                ),
            )
            sendBroadcast(intent)
        }
    }

    /**
     * Information about the track that was playing while
     * app was in background. Call this before the job is cancelled
     */
    fun getTrackData(trackData: (track: Track?) -> Unit) {
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { playerState ->
            trackData(playerState.track)
        }?.setErrorCallback { throwable ->
            Timber.e(throwable.message)
        }
    }

    inner class PlaybackBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    companion object {
        const val CHANNEL_ID = "playback-service-channel-id"
        const val NOTIFICATION_ID = 888
        const val PLAYER_STATE = "player-state"
        const val TRACK_DATA = "track-data"
    }

    @Parcelize
    data class Player(
        val imageUri: String?,
        val trackArtist: String?,
        val albumName: String?,
        val trackName: String?,
        val trackUri: String?,
        val isTrackPaused: Boolean = true,
        val position: Long = 0L,
        val duration: Long = 0L,
    ) : Parcelable
}