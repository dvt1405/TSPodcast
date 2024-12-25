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
import javax.inject.Qualifier


@Module
@InstallIn(SingletonComponent::class)
abstract class AbsRadioModule {
    @Binds
    @IntoMap
    @RadioRepoMapKey(RadioRepo.VOV)
    abstract fun bindVOVApi(repo: RadioRepository): RadioApi

    @Binds
    @RadioRepoQualifier(RadioRepo.VOV)
    abstract fun bindVOVApiS(repo: RadioRepository): RadioApi

    @Binds
    @IntoMap
    @RadioRepoMapKey(RadioRepo.VOH)
    abstract fun bindVOHApi(repo: VOHRepository): RadioApi
}

@MapKey
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class RadioRepoMapKey(val value: RadioRepo = RadioRepo.VOV) {}

@Qualifier
@Target(
    AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.RUNTIME)
annotation class RadioRepoQualifier(val value: RadioRepo = RadioRepo.VOV) {}

@Keep
enum class RadioRepo {
    VOV,
    VOH
}