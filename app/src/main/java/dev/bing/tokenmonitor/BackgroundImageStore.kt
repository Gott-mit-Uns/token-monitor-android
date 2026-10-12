package dev.bing.tokenmonitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.util.AtomicFile
import java.io.*
import java.nio.ByteBuffer
import kotlin.math.roundToInt

internal object BackgroundImageRules {
    const val sourceBytes = 12 * 1024 * 1024
    const val savedBytes = 8 * 1024 * 1024
    const val edge = 2000
    const val defaultOpacity = 28
    fun opacity(value: Int) = value.coerceIn(0, 100)
    fun size(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && width <= 32768 && height <= 32768 && width.toLong() * height <= 120_000_000)
        val scale = minOf(1.0, edge.toDouble() / width, edge.toDouble() / height)
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }
}

internal data class BackgroundImageRecord(val opacity: Int = 28, val png: ByteArray? = null)

/** One atomic private record; removing a picture retains the independently chosen opacity. */
internal class BackgroundImageStore(context: Context) {
    private companion object { val diskLock = Any() }
    private val file = AtomicFile(File(context.noBackupFilesDir, "background-image.bin"))
    fun load(): BackgroundImageRecord = synchronized(diskLock) {
        if (!file.baseFile.exists()) return BackgroundImageRecord()
        require(file.baseFile.length() in 16..(BackgroundImageRules.savedBytes + 16).toLong())
        return DataInputStream(file.openRead()).use { input ->
            require(input.readInt() == 0x544d4247 && input.readInt() == 1)
            val opacity = input.readInt(); require(opacity in 0..100)
            val count = input.readInt(); require(count in 0..BackgroundImageRules.savedBytes)
            val bytes = if (count == 0) null else ByteArray(count).also(input::readFully)
            require(input.read() == -1)
            BackgroundImageRecord(opacity, bytes)
        }
    }
    fun save(record: BackgroundImageRecord) = synchronized(diskLock) {
        require(record.opacity in 0..100 && (record.png == null || record.png.size in 1..BackgroundImageRules.savedBytes))
        val stream = file.startWrite()
        try {
            val out = DataOutputStream(stream)
            out.writeInt(0x544d4247); out.writeInt(1); out.writeInt(record.opacity)
            out.writeInt(record.png?.size ?: 0); record.png?.let(out::write); out.flush()
            file.finishWrite(stream)
        } catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }
}

internal object BackgroundImageDecoder {
    fun read(source: InputStream): ByteArray = source.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            require(output.size().toLong() + count <= BackgroundImageRules.sourceBytes)
            output.write(buffer, 0, count)
        }
        output.toByteArray().also { require(it.isNotEmpty()) }
    }
    fun decode(bytes: ByteArray): Bitmap {
        require(bytes.size in 1..BackgroundImageRules.sourceBytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val (width, height) = BackgroundImageRules.size(bounds.outWidth, bounds.outHeight)
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val target = BackgroundImageRules.size(info.size.width, info.size.height)
            decoder.setTargetSize(target.first, target.second)
        }
        var sample = 1
        while (bounds.outWidth / sample > BackgroundImageRules.edge || bounds.outHeight / sample > BackgroundImageRules.edge) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
            }
        }
        val oriented = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
        val target = if (orientation in 5..8) height to width else width to height
        return if (oriented.width == target.first && oriented.height == target.second) oriented else Bitmap.createScaledBitmap(oriented, target.first, target.second, true).also { if (it !== oriented) oriented.recycle() }
    }
    fun png(bitmap: Bitmap): ByteArray {
        val bytes = ByteArrayOutputStream()
        val limited = object : OutputStream() {
            override fun write(value: Int) { require(bytes.size() < BackgroundImageRules.savedBytes); bytes.write(value) }
            override fun write(data: ByteArray, offset: Int, length: Int) { require(bytes.size().toLong() + length <= BackgroundImageRules.savedBytes); bytes.write(data, offset, length) }
        }
        require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, limited))
        return bytes.toByteArray().also { require(it.isNotEmpty()) }
    }
}
