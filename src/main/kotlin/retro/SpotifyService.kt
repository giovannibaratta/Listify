package retro

import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface SpotifyService {

    @GET("playlists/{playlist_id}/tracks")
    fun getPlaylistTracks(
        @Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Call<PlaylistTracks?>?

    @GET("me/playlists")
    fun getMePlaylists(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Call<PlaylistItems?>?


    @GET("playlists/{playlist_id}")
    fun getPlaylistInfo(
        @Path("playlist_id") playlistId: String
    ): Call<PlaylistInfo?>?

    @POST("users/{user_id}/playlists")
    fun createPlaylist(
        @Path("user_id") userId: String,
        @Body playlist: CreatePlaylistRequest
    ): Call<CreatePlaylistResponse>

    @POST("playlists/{playlist_id}/tracks")
    fun addTracksToPlaylist(
        @Path("playlist_id") playlistId: String,
        @Body body: PostAddTracksToPlaylist
    ): Call<ResponseAddTracksToPlaylist>

    companion object {
        fun Build(token: String): SpotifyService {

            // build retrofit
            val clientWithBearer = OkHttpClient.Builder().addInterceptor {
                val request = it.request().newBuilder().addHeader(
                    "Authorization", "Bearer " + token
                ).build()
                it.proceed(request)
            }.build()

            val retrofit = Retrofit.Builder()
                .client(clientWithBearer)
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://api.spotify.com/v1/")
                .build()

            return retrofit.create(SpotifyService::class.java)
        }
    }
}