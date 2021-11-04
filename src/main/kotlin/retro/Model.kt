package retro

data class PlaylistTracks(val href: String, val total: Int, val items: List<TrackInfo>)

data class TrackInfo(val track: Track)
data class Track(val name: String, val id: String)

data class CreatePlaylistRequest(val name: String, val description: String, val public: Boolean)
data class CreatePlaylistResponse(val name: String, val description: String, val public: Boolean, val id: String)

data class PlaylistItems(val href: String, val total: Int, val items: List<PlaylistInfo>)
data class PlaylistInfo(
    val collaborative: Boolean,
    val description: String,
    val id: String,
    val name: String,
    val public: Boolean
)

class PostAddTracksToPlaylist(tracks: List<Track>) {
    val uris: List<String>

    init {
        uris = tracks.map { "spotify:track:${it.id}" }
    }
}

data class ResponseAddTracksToPlaylist(val snapshot_id: String)