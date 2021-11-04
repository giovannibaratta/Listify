package org.example

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.DataStoreFactory
import com.google.api.client.util.store.FileDataStoreFactory
import retro.*
import java.io.File
import kotlin.system.exitProcess


fun getTracksRestCall(spotifyService: SpotifyService, playlistId: String, offset: Int): PlaylistTracks {
    val call = spotifyService.getPlaylistTracks(playlistId, offset = offset)?.execute()
    require(call != null)
    require(call.isSuccessful)
    return call.body()!!
}

fun getTracks(spotifyService: SpotifyService, playlistInfo: PlaylistInfo): List<Track> {
    val tracks = mutableListOf<Track>()
    // first call
    var playlist = getTracksRestCall(spotifyService, playlistInfo.id, 0)

    playlist.items.forEach { tracks.add(it.track) }

    // loop until end
    while (tracks.size < playlist.total) {
        playlist = getTracksRestCall(spotifyService, playlistInfo.id, offset = tracks.size)
        playlist.items.forEach { tracks.add(it.track) }
    }

    println("Playlist ${playlistInfo.name} has ${tracks.size} tracks:")
    tracks.forEach { println("\t- ${it.name}") }

    return tracks
}

fun getPlaylistRestCall(spotifyService: SpotifyService, offset: Int): PlaylistItems {
    val call = spotifyService.getMePlaylists(offset = offset)?.execute()
    require(call != null)
    require(call.isSuccessful, { "getPlaylistError: ${call.errorBody()?.string()}" })
    return call.body()!!
}

// Get current user playlist
fun getUserPlaylists(spotifyService: SpotifyService): List<PlaylistInfo> {
    val playlists = mutableListOf<PlaylistInfo>()
    // first call
    var partialList = getPlaylistRestCall(spotifyService, 0)

    partialList.items.forEach { playlists.add(it) }

    // loop until end
    while (playlists.size < partialList.total) {
        partialList = getPlaylistRestCall(spotifyService, offset = playlists.size)
        partialList.items.forEach { playlists.add(it) }
    }

    println("User has ${playlists.size} playlist:")
    playlists.forEach { println("\t- ${it.name}") }

    return playlists
}


fun getPlaylistInfo(service: SpotifyService, playlistId: String): PlaylistInfo {
    val call = service.getPlaylistInfo(playlistId)

    require(call != null)

    val res = call.execute()

    require(res.isSuccessful, { println("getPlaylistInfo failed: ${res.errorBody()?.string()}") })

    return res.body()!!
}

// return playlist id
fun createTargetPlaylist(service: SpotifyService, userId: String, playlistName: String): String {
    // check if playlist already exists
    for (playlist in getUserPlaylists(service)) {
        if (playlist.name == playlistName && playlist.public) {
            println("Playlist already exists")
            return playlist.id
        }
    }

    val post = service.createPlaylist(userId, CreatePlaylistRequest(playlistName, "Autogenerated playlist", true))
    val req = post.execute()
    require(req.isSuccessful)
    return req.body()!!.id
}

fun addTracksRestCall(service: SpotifyService, playlistId: String, tracks: List<Track>): String {
    val post = service.addTracksToPlaylist(playlistId, PostAddTracksToPlaylist(tracks))
    val req = post.execute()
    require(req.isSuccessful)
    return req.body()!!.snapshot_id
}

fun addTracksToPlaylist(service: SpotifyService, playlistId: String, tracks: List<Track>) {
    var addedTracks = 0

    while (addedTracks < tracks.size) {
        val limit = if (addedTracks + 100 < tracks.size) addedTracks + 100 else tracks.size
        addTracksRestCall(service, playlistId, tracks.subList(addedTracks, limit))
        //println("Added $limit tracks")
        addedTracks += limit
    }
}

fun getOAuthToken(apiKey: String, apiSecret: String): String {
    val HTTP_TRANSPORT: HttpTransport = NetHttpTransport()

    val JSON_FACTORY: JsonFactory = GsonFactory()

    val DATA_STORE_FACTORY: DataStoreFactory = FileDataStoreFactory(File("./oauth_token"))

    val flow = AuthorizationCodeFlow.Builder(
        BearerToken.authorizationHeaderAccessMethod(),
        HTTP_TRANSPORT,
        JSON_FACTORY,
        GenericUrl(TOKEN_SERVER_URL),
        ClientParametersAuthentication(
            apiKey, apiSecret
        ),
        apiSecret,
        AUTHORIZATION_SERVER_URL
    )
        .setScopes(SCOPE)
        .setDataStoreFactory(DATA_STORE_FACTORY)
        .build()

    // authorize
    val receiver = LocalServerReceiver.Builder()
        .setHost("localhost")
        .setCallbackPath("/callback")
        .setPort(8888)
        .build()

    val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

    println("Got OAuth token")

    println("Token expires in ${credential.expiresInSeconds} s (negative means expired).")

    if (credential.expiresInSeconds < 60 * 5) {
        println("Refreshing token")
        credential.refreshToken()
        println("Got a new token")
    }

    return credential.accessToken
}

private const val API_KEY_INDEX = 0
private const val API_SECRET_INDEX = 1
private const val USER_INDEX = 2
private const val PLAYLIST_INDEX = 3
private const val TARGET_PLAYLIST = 4

private const val CONFIG_FILE = "./config"
private const val TOKEN_SERVER_URL = "https://accounts.spotify.com/api/token"
private const val AUTHORIZATION_SERVER_URL = "https://accounts.spotify.com/authorize"
private val SCOPE = listOf("user-read-email", "playlist-modify-public")


fun main() {

    val configFile = File(CONFIG_FILE)

    require(configFile.exists(), { "Configuration file does not exist." })

    val configFileContect = File("./config").readLines()
    val apiKey = configFileContect[API_KEY_INDEX].replace("API_KEY=", "").trim()
    val apiSecret = configFileContect[API_SECRET_INDEX].replace("API_SECRET=", "").trim()
    val user = configFileContect[USER_INDEX].replace("USER=", "").trim()
    val playlist = configFileContect[PLAYLIST_INDEX].replace("PLAYLIST=", "")
        .split(",")
        .map {
            it.trim()

            if (it.startsWith("https://open.spotify.com/playlist/"))
                return@map it.split("/")[4].split("?")[0]
            it
        }

    val targetPlaylist = configFileContect[TARGET_PLAYLIST].replace("TARGET_PLAYLIST_NAME=", "").trim()

    require(playlist.size > 0, { "You have to specify at least one public playlist" })
    require(targetPlaylist.length > 0, { "Target playlist can not be empty" })
    require(apiKey.length > 0, { "Api key can not be empty" })
    require(apiSecret.length > 0, { "Api secret can not be empty" })
    require(user.length > 0, { "User can not be empty" })

    val token = getOAuthToken(apiKey, apiSecret)

    println("")

    val service = SpotifyService.Build(token)

    // generate track list from source playlist
    val uniqueTracks = mutableSetOf<Track>()

    playlist.map { getTracks(service, getPlaylistInfo(service, it)) }.forEach { trackList ->
        trackList.forEach {
            uniqueTracks.add(it)
        }
    }

    println("")
    println("Total tracks: ${uniqueTracks.size}")
    //uniqueTracks.forEach { println(it) }

    // Create target playlist if not exists
    val targetPlaylistID = createTargetPlaylist(service, user, targetPlaylist)

    println("")
    println("Target playlist id: ${targetPlaylistID}")

    val targetPlaylistInfo = getPlaylistInfo(service, targetPlaylistID)

    // Get songs from TARGET_PLAYLIST
    val targetTracksList = getTracks(service, targetPlaylistInfo)

    val delta = uniqueTracks.subtract(targetTracksList)

    println("")
    println("New tracks to add ${delta.size}")
    delta.forEach { println("\t- ${it.name}") }

    addTracksToPlaylist(service, targetPlaylistID, delta.toList())

    println("Success")
    exitProcess(0)
}