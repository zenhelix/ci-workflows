package dsl

class MatrixRef(val key: String) {
    val ref: String get() = "\${{ matrix.$key }}"
}

data class MatrixDef(val entries: Map<String, String>)
