package dsl.core

@JvmInline
value class MatrixRefExpr(val expression: String)

class MatrixRef(val key: String) {
    val ref: MatrixRefExpr get() = MatrixRefExpr("\${{ matrix.$key }}")
}

data class MatrixDef(val entries: Map<String, String>)

val MatrixRef.expr: String get() = ref.expression
