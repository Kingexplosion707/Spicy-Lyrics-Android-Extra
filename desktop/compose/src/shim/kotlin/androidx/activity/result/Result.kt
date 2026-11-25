package androidx.activity.result

fun interface ActivityResultLauncher<I> {
    fun launch(input: I)
    fun launch(input: I, options: Any?) = launch(input)
}
