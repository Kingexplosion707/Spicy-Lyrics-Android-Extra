package androidx.lifecycle

import android.app.Application

/** A view model with the application, as on Android. */
open class AndroidViewModel(private val application: Application) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    open fun <T : Application> getApplication(): T = application as T
}
