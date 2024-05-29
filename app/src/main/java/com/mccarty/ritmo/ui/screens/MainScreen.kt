package com.mccarty.ritmo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.mccarty.ritmo.MainActivity
import com.bumptech.glide.integration.compose.GlideImage as GlideImage
import com.mccarty.ritmo.MainViewModel.RecentlyPlayedMusicState.Success as Success
import com.mccarty.ritmo.MainViewModel.AllPlaylistsState.Success as PlaylistSuccess
import com.mccarty.ritmo.R
import com.mccarty.ritmo.MainViewModel

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MainScreen(
    model: MainViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val recentlyPlayedMusic by model.recentlyPlayedMusic.collectAsStateWithLifecycle()
    val allPlayLists by model.allPlaylists.collectAsStateWithLifecycle()
    val musicHeader by model.musicHeader.collectAsStateWithLifecycle()

    LazyColumn {
        item {
            MainHeader(
                imageUrl = musicHeader.imageUrl.toString(),
                artistName = musicHeader.artistName,
                albumName = musicHeader.albumName,
                songName = musicHeader.songName,
                modifier = Modifier,
            )
        }

        when (recentlyPlayedMusic) {
            is MainViewModel.RecentlyPlayedMusicState.Pending -> {
                println("MainScreen ***** PENDING")
            }

            is Success<*> -> {
                val tracks = (recentlyPlayedMusic as Success<*>).data.items
                item {
                    MediaList(tracks, onTrackClick = { index ->
                        navController.navigate("${MainActivity.SONG_DETAILS_KEY}${index}")
                    }, onViewMoreClick = { action ->
                        model.trackSelectAction(action)
                    })
                }
            }

            else -> {
                println("MainScreen ***** ERROR")
            }
        }

        when (allPlayLists) {
            is MainViewModel.AllPlaylistsState.Pending -> {
                println("MainScreen ***** PLAYLIST PENDING")
            }

            is PlaylistSuccess -> {
                val playlist = (allPlayLists as MainViewModel.AllPlaylistsState.Success).playLists

                if (playlist.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.playlists),
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Normal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .paddingFromBaseline(top = 40.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
                // TODO: make reusable
                playlist.forEachIndexed { index, item ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .clickable(onClick = {
                                    model.fetchPlaylist(playlist[index].id)
                                    navController.navigate("${MainActivity.PLAYLIST_SCREEN_KEY}${playlist[index].id}")
                                }),
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            val imageUrl = item.images.firstOrNull()?.url
                            Row {
                                GlideImage(
                                    model = imageUrl,
                                    contentDescription = "", // TODO: add description
                                    modifier = Modifier
                                        .size(100.dp),
                                )

                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .paddingFromBaseline(top = 25.dp)
                                            .fillMaxWidth(),
                                    )
                                    if (item.description.isNotEmpty()) {
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .paddingFromBaseline(top = 25.dp)
                                                .fillMaxWidth(),
                                        )
                                    }
                                    Text(
                                        text = "${stringResource(R.string.total_tracks)} ${item.tracks.total}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .paddingFromBaseline(top = 25.dp)
                                            .fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

/*                for (item in playlist) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .clickable(onClick = {
                                    navController.navigate("playlist_screen")
                                }),
                            shape = MaterialTheme.shapes.extraSmall,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            val imageUrl = item.images.firstOrNull()?.url
                            Row {
                                GlideImage(
                                    model = imageUrl,
                                    contentDescription = "", // TODO: add description
                                    modifier = Modifier
                                        .size(100.dp),
                                )

                                Column(modifier = Modifier.padding(start = 20.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .paddingFromBaseline(top = 25.dp)
                                            .fillMaxWidth(),
                                    )
                                    if (item.description.isNotEmpty()) {
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .paddingFromBaseline(top = 25.dp)
                                                .fillMaxWidth(),
                                        )
                                    }
                                    Text(
                                        text = "${stringResource(R.string.total_tracks)} ${item.tracks.total}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .paddingFromBaseline(top = 25.dp)
                                            .fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }*/
            }

            else -> {
                println("MainScreen ***** PLAYLIST ERROR")
            }
        }
    }
}