
package com.example.zee5

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class Zee5 : MainAPI() {
    override var name = "Zee5"
    override var mainUrl = "https://www.zee5.com"
    override val supportedTypes = setOf(TvType.Live, TvType.Movie, TvType.TvSeries)

    private val catalogBase = "https://catalogapi.zee5.com/v1"
    private val tokenUrl = "https://useraction.zee5.com/token/platform_tokens.php?platform_name=web_app"

    private fun getToken(): String {
        val res = app.get(tokenUrl, headers = mapOf("User-Agent" to USER_AGENT)).parsed<Map<String, String>>()
        return res["token"] ?: ""
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val token = getToken()
        val headers = mapOf("x-access-token" to token)

        val homeList = mutableListOf<HomePageList>()

        val liveUrl = "$catalogBase/channel/bygenre?country=IN&translation=en"
        val liveJson = app.get(liveUrl, headers = headers).parsedSafe<JsonObject>() ?: return HomePageResponse(emptyList())
        val items = liveJson["items"]?.jsonArray ?: return HomePageResponse(emptyList())

        val liveItems = items.flatMap { genre ->
            genre.jsonObject["items"]?.jsonArray?.mapNotNull { ch ->
                val obj = ch.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val img = "https://akamaividz2.zee5.com/image/upload/resources/$id/channel_web/${obj["list_image"]?.jsonPrimitive?.contentOrNull}"

                LiveSearchResponse(title, id, this.name, TvType.Live, img)
            } ?: emptyList()
        }
        homeList.add(HomePageList("Live TV", liveItems))

        return HomePageResponse(homeList)
    }

    override suspend fun load(url: String): LoadResponse {
        val streamData = app.get("$catalogBase/channel/$url?translation=en&country=IN").parsedSafe<JsonObject>()
        val streamUrl = streamData?.get("stream_url_hls")?.jsonPrimitive?.contentOrNull ?: return LiveStreamLoadResponse("Zee5", url, url, this.name)

        val liveToken = app.get("https://useraction.zee5.com/token/live.php").parsed<Map<String, String>>()
        val fullStreamUrl = "$streamUrl${liveToken["video_token"]}"

        return LiveStreamLoadResponse("Zee5 Live", url, fullStreamUrl, this.name)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
    }
}
