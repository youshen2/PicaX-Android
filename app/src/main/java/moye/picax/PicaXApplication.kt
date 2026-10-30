package moye.picax

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import moye.picax.data.AppStore
import moye.picax.data.ComicRepository

class PicaXApplication : Application(), SingletonImageLoader.Factory {
    val store by lazy { AppStore(this) }
    val repository by lazy { ComicRepository(store) }
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { repository.http.imageClient() })) }.build()
}
