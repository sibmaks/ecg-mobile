package xyz.dma.ecgmobile.utils

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtils {
    private const val BUFFER_SIZE = 2048

    fun zip(files: List<File>, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            val data = ByteArray(BUFFER_SIZE)
            for (i in files.indices) {
                val fi = FileInputStream(files[i])
                BufferedInputStream(fi, BUFFER_SIZE).use { origin ->
                    val filename = files[i].absolutePath
                    val entry = ZipEntry(filename.substring(filename.lastIndexOf("/") + 1))
                    out.putNextEntry(entry)
                    var count: Int
                    while (origin.read(data, 0, BUFFER_SIZE).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                }
            }
        }
    }
}