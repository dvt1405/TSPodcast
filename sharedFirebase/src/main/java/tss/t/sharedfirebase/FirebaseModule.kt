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
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.ANNOTATION_CLASS,
)
annotation class FirebaseScope(
    val value: String
)

object FirebaseDispatcher {
    const val IO = "IO"
    const val Default ="Default"
}
