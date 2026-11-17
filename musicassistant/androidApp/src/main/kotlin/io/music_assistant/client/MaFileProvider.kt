package io.music_assistant.client

import androidx.core.content.FileProvider

/** Music Assistant's own file provider, apart from the host app's (one class can't be declared twice). */
class MaFileProvider : FileProvider()
