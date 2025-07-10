package tss.t.coreradio.di

import androidx.annotation.Keep
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import tss.t.coreradio.api.RadioApi
import tss.t.coreradio.repository.RadioRepository
import tss.t.coreradio.repository.VOHRepository
import javax.inject.Named


@Module
@InstallIn(SingletonComponent::class)
abstract class AbsRadioModule {
    @Binds
    @IntoMap
    @RadioMapKey(RadioRepo.VOV)
    abstract fun bindVOVApi(repo: RadioRepository): RadioApi

    @Binds
    @Named(RadioRepo.VOV)
    abstract fun bindVOVApiS(repo: RadioRepository): RadioApi

    @Binds
    @IntoMap
    @RadioMapKey(RadioRepo.VOH)
    abstract fun bindVOHApi(repo: VOHRepository): RadioApi
}

@MapKey
annotation class RadioMapKey(val key: String)

@Keep
object RadioRepo {
    const val VOV = "VOV"
    const val VOH = "VOH"
}
