package array.msofficereader

import array.*
import array.builtins.TagCatch
import array.builtins.makeBoolean
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileNotFoundException

fun readExcelFile(name: String): APLValue {
    val workbook = WorkbookFactory.create(File(name))
    val evaluator = workbook.creationHelper.createFormulaEvaluator()
    val sheet = workbook.getSheetAt(0)
    if (sheet.physicalNumberOfRows == 0) {
        return APLNullValue.APL_NULL_INSTANCE
    }

    val lastRowIndex = sheet.lastRowNum
    val rows = ArrayList<List<APLValue>>()
    for (i in 0..lastRowIndex) {
        val row = readRow(sheet.getRow(i), evaluator)
        rows.add(row)
    }

    val width = rows.maxValueBy { it.size }
    return APLArrayImpl.make(dimensionsOfSize(rows.size, width)) { i ->
        val rowIndex = i / width
        val colIndex = i % width
        val row = rows[rowIndex]
        if (colIndex < row.size) {
            row[colIndex]
        } else {
            APLNullValue.APL_NULL_INSTANCE
        }
    }
}

fun readRow(row: Row, evaluator: FormulaEvaluator): List<APLValue> {
    val cellList = ArrayList<APLValue>()
    val lastCellIndex = row.lastCellNum
    var numPendingNulls = 0
    for (i in 0 until lastCellIndex) {
        val cell = row.getCell(i)
        if (cell == null) {
            numPendingNulls++
        } else {
            repeat(numPendingNulls) {
                cellList.add(APLNullValue.APL_NULL_INSTANCE)
            }
            numPendingNulls = 0
            cellList.add(cellToAPLValue(cell, evaluator))
        }
    }
    return cellList
}

fun cellToAPLValue(cell: Cell, evaluator: FormulaEvaluator): APLValue {
    return when (cell.cellType) {
        CellType.FORMULA -> parseEvaluatedCell(cell, evaluator)
        CellType.BOOLEAN -> makeBoolean(cell.booleanCellValue)
        CellType.BLANK -> APLLONG_0
        CellType.NUMERIC -> APLDouble(cell.numericCellValue)
        CellType.STRING -> APLString.make(cell.stringCellValue)
        else -> throw IllegalStateException("Unknown cell type: ${cell.cellType}")
    }
}

fun parseEvaluatedCell(cell: Cell, evaluator: FormulaEvaluator): APLValue {
    val v = evaluator.evaluate(cell)
    return when (cell.cellType) {
        CellType.FORMULA -> throw IllegalStateException("The result of an evaluation should not be a formula")
        CellType.BOOLEAN -> (if (v.booleanValue) 1 else 0).makeAPLNumber()
        CellType.BLANK -> APLLONG_0
        CellType.NUMERIC -> v.numberValue.makeAPLNumber()
        CellType.STRING -> APLString.make(v.stringValue)
        else -> throw IllegalStateException("Unknown cell type: ${v.cellType}")
    }
}

class LoadExcelFileFunction : APLFunctionDescriptor {
    class LoadExcelFileFunctionImpl(pos: Position) : NoAxisAPLFunction(pos) {
        override fun eval1Arg(context: RuntimeContext, a: APLValue): APLValue {
            val filename = arrayToString(a)
            try {
                return readExcelFile(filename)
            } catch (e: FileNotFoundException) {
                throwAPLException(
                    TagCatch(
                        APLSymbol(context.engine.internSymbol("fileNotFound", context.engine.keywordNamespace)),
                        APLString(filename),
                        "File not found: ${filename}",
                        pos))
            }
        }

        private fun arrayToString(a: APLValue): String {
            if (a.rank != 1) {
                throwAPLException(InvalidDimensionsException("String must be rank 1", pos))
            }
            val buf = StringBuilder()
            for (i in 0 until a.size) {
                val charValue = a.valueAt(i)
                if (charValue !is APLChar) {
                    throwAPLException(IncompatibleTypeException("Value at position $i is not a character", pos))
                }
                buf.addCodepoint(charValue.value)
            }
            return buf.toString()
        }

        override fun eval2Arg(context: RuntimeContext, a: APLValue, b: APLValue): APLValue {
            TODO("not implemented")
        }
    }

    override fun make(pos: Position) = LoadExcelFileFunctionImpl(pos)
}

class MsOfficeModule : KapModule {
    override val name get() = "msoffice"

    override fun init(engine: Engine) {
        val ns = engine.makeNamespace("msoffice")
        engine.registerFunction(engine.internSymbol("read", ns), LoadExcelFileFunction())
    }
}
