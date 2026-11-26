@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.activity.result.contract

import android.net.Uri
import com.tx24.spicyplayer.desktop.platform.FilePicker

/** What a launcher does on the desktop: a file dialog, or an answer straight away. */
abstract class ActivityResultContract<I, O> {
    abstract fun run(input: I, done: (O) -> Unit)
}

object ActivityResultContracts {
    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) = FilePicker.open(arrayOf(input), done)
    }
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(input: Array<String>, done: (Uri?) -> Unit) = FilePicker.open(input, done)
    }
    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun run(input: Array<String>, done: (List<Uri>) -> Unit) = FilePicker.open(input) { done(listOfNotNull(it)) }
    }
    class CreateDocument(private val mime: String = "*/*") : ActivityResultContract<String, Uri?>() {
        override fun run(input: String, done: (Uri?) -> Unit) = FilePicker.save(input, done)
    }
    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>() {
        override fun run(input: Uri?, done: (Uri?) -> Unit) = FilePicker.folder(done)
    }
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun run(input: String, done: (Boolean) -> Unit) = done(true)
    }
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun run(input: Array<String>, done: (Map<String, Boolean>) -> Unit) = done(input.associateWith { true })
    }
    class StartActivityForResult : ActivityResultContract<android.content.Intent, ActivityResult>() {
        override fun run(input: android.content.Intent, done: (ActivityResult) -> Unit) { android.content.Context().startActivity(input); done(ActivityResult(0, null)) }
    }
    class PickVisualMedia : ActivityResultContract<Any?, Uri?>() {
        override fun run(input: Any?, done: (Uri?) -> Unit) = FilePicker.open(arrayOf("image/*"), done)
        object ImageOnly
    }
}

class ActivityResult(val resultCode: Int, val data: android.content.Intent?)
