package tss.t.sharedfirebase

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import tss.t.sharedlibrary.utils.ConfigAPI
import javax.inject.Qualifier

@Module
@InstallIn(SingletonComponent::class)
class FirebaseModule {
    @Provides
    @FirebaseScope(FirebaseDispatcher.IO)
    fun firebaseCoroutineScope() = CoroutineScope(Dispatchers.IO)

    @Provides
    @FirebaseScope(FirebaseDispatcher.Default)
    fun firebaseCoroutineScopeDefault() = CoroutineScope(Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AbsFirebaseModule {
    @Binds
    abstract fun bindRemoteConfig(
        tsFirebaseRemoteConfig: TSFirebaseRemoteConfig
    ): ConfigAPI
}

@Qualifier
annotation class FirebaseScope(
    val value: FirebaseDispatcher
)

enum class FirebaseDispatcher {
    IO, Default
}