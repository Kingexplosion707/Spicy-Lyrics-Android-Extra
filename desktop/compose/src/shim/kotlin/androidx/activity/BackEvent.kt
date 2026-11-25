package androidx.activity
class BackEventCompat(val touchX: Float = 0f, val touchY: Float = 0f, val progress: Float = 0f, val swipeEdge: Int = EDGE_LEFT) {
    companion object { const val EDGE_LEFT = 0; const val EDGE_RIGHT = 1; const val EDGE_NONE = 2 }
}
