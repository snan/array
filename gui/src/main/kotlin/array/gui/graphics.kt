package array.gui

import array.*
import kotlin.math.max
import kotlin.math.min
import javafx.application.Platform
import javafx.beans.property.DoubleProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.event.EventType
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.image.WritableImage
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.WindowEvent
import java.util.concurrent.atomic.AtomicReference

class GraphicWindowAPLValue(width: Int, height: Int) : APLSingleValue() {
    private val window: GraphicWindow

    init {
        window = GraphicWindow(width, height)
    }

    override val aplValueType: APLValueType
        get() = APLValueType.INTERNAL

    override fun formatted(style: FormatStyle) = "graphic-window"

    override fun compareEquals(reference: APLValue) = reference is GraphicWindowAPLValue && window === reference.window

    override fun makeKey() = window

    fun updateContent(w: Int, h: Int, content: DoubleArray) {
        Platform.runLater {
            window.repaintContent(w, h, content)
        }
    }
}

class MakeGraphicFunction : APLFunctionDescriptor {
    class MakeGraphicFunctionImpl(pos: Position) : NoAxisAPLFunction(pos) {
        override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
            val aDimensions = a.dimensions
            if (aDimensions.size != 1 || aDimensions[0] != 2) {
                throw InvalidDimensionsException("Argument must be a two-element vector")
            }
            val width = a.valueAt(0).ensureNumber(pos).asInt()
            val height = a.valueAt(1).ensureNumber(pos).asInt()
            return GraphicWindowAPLValue(width, height)
        }
    }

    override fun make(pos: Position) = MakeGraphicFunctionImpl(pos)
}

class DrawGraphicFunction : APLFunctionDescriptor {
    class DrawGraphicFunctionImpl(pos: Position) : NoAxisAPLFunction(pos) {
        override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
            val v = a.unwrapDeferredValue()
            if (v !is GraphicWindowAPLValue) {
                throw APLIncompatibleDomainsException("Left argument must be a graphic object", pos)
            }
            val bDimensions = b.dimensions
            if (bDimensions.size != 2) {
                throw InvalidDimensionsException("Right argument must be a two-dimensional array", pos)
            }
            v.updateContent(bDimensions[1], bDimensions[0], b.toDoubleArray(pos))
            return b
        }
    }

    // ∇ range (low;high;v) { low+(⍳v)÷(v÷(high-low)) }
    // ∇ m (x) { z←r←n←0 ◊ while((r ≤ 2) ∧ (n < 20)) { z ← x+z⋆2 ◊ r ← |z ◊ n←n+1} ◊ n÷20 }
    // m¨(0J1×range(-2;2;200)) ∘.+ range(-2;2;200)
    override fun make(pos: Position) = DrawGraphicFunctionImpl(pos)
}

class GraphicWindow(width: Int, height: Int) {
    private var content = AtomicReference<Content?>()

    private val colourmap = (0..255).map { index ->
        val v = index / 255.0
        Color(v, v, v, 1.0)
    }

    init {
        Platform.runLater {
            content.set(Content(width, height))
        }
    }

    private inner class Content(width: Int, height: Int) {
        val stage = Stage()
        val canvas: Canvas

        init {
            canvas = Canvas(width.toDouble(), height.toDouble())
            val resizeListener = ChangeListener<Number> { observable, oldValue, newValue -> println("resizing") }
            canvas.widthProperty().addListener(resizeListener)
            canvas.heightProperty().addListener { _, old, new -> println("canvas change: $old -> $new") }
            canvas.maxWidth(Double.MAX_VALUE)
            canvas.maxHeight(Double.MAX_VALUE)
            VBox.setVgrow(canvas, Priority.ALWAYS)
            canvas.style = "-fx-background-color: red"

            val border = VBox(canvas)
            border.widthProperty().addListener { _, old, new -> println("border change: $old -> $new") }
            border.style = "-fx-background-color: green"
            stage.scene = Scene(border, width.toDouble(), height.toDouble())
            stage.widthProperty().addListener { _, old, new -> println("stage change: $old -> $new") }
            stage.show()
        }

        fun repaintCanvas(width: Int, height: Int, array: DoubleArray) {
            val image = WritableImage(canvas.width.toInt(), canvas.height.toInt())
            val imageWidth = image.width
            val imageHeight = image.height
            val xStride = width.toDouble() / imageWidth
            val yStride = height.toDouble() / imageHeight
            val pixelWriter = image.pixelWriter
            for (y in 0 until imageHeight.toInt()) {
                for (x in 0 until imageWidth.toInt()) {
                    val value = array[(y * yStride).toInt() * width + (x * xStride).toInt()]
                    val valueByte = min(max(value * 256, 0.0), 255.0).toInt() and 0xFF
                    val colour = (0xFF shl 24) or (valueByte shl 16) or (valueByte shl 8) or valueByte
                    pixelWriter.setArgb(x, y, colour)
                }
            }
            canvas.graphicsContext2D.drawImage(image, 0.0, 0.0)
        }
    }

    fun repaintContent(width: Int, height: Int, array: DoubleArray) {
        content.get()?.repaintCanvas(width, height, array)
    }
}

fun initGraphicCommands(client: Client) {
    val engine = client.engine
    val guiNamespace = engine.makeNamespace("gui")
    engine.registerFunction(engine.internSymbol("makeGraphic", guiNamespace), MakeGraphicFunction())
    engine.registerFunction(engine.internSymbol("drawArray", guiNamespace), DrawGraphicFunction())
}
