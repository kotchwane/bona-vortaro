/*
 * Bona vortaro - an Esperanto dictionary for Android, based on PReVo
 * Copyright (C) 2012  Neil Roberts
 * Copyright (C) 2026  kotchwane
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.kotchwane.bonavortaro

import java.io.IOException
import java.io.InputStream

/** Reads the numbers of prevodb's files, in little endian, and counts the
 * bytes read */
class BinaryReader(private val input: InputStream) {
    var position = 0L
        private set

    private val numberBuffer = ByteArray(4)

    @Throws(IOException::class)
    fun readAll(array: ByteArray, offset: Int, length: Int) {
        var at = offset
        var left = length
        while (left > 0) {
            val got = input.read(array, at, left)
            if (got == -1)
                throw IOException("Unexpected EOF")
            position += got
            at += got
            left -= got
        }
    }

    @Throws(IOException::class)
    fun readAll(array: ByteArray) = readAll(array, 0, array.size)

    @Throws(IOException::class)
    fun readByte(): Int {
        val ret = input.read()
        if (ret == -1)
            throw IOException("Unexpected EOF")
        position += 1
        return ret
    }

    @Throws(IOException::class)
    fun readShort(): Int {
        readAll(numberBuffer, 0, 2)
        return (numberBuffer[0].toInt() and 0xff) or ((numberBuffer[1].toInt() and 0xff) shl 8)
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        readAll(numberBuffer, 0, 4)
        return (numberBuffer[0].toInt() and 0xff) or
            ((numberBuffer[1].toInt() and 0xff) shl 8) or
            ((numberBuffer[2].toInt() and 0xff) shl 16) or
            ((numberBuffer[3].toInt() and 0xff) shl 24)
    }

    @Throws(IOException::class)
    fun skip(byteCount: Long) {
        /* First try skipping with the input stream's method */
        val skipped = input.skip(byteCount)
        position += skipped
        var left = byteCount - skipped

        /* If that doesn't work, read into a buffer */
        if (left > 0) {
            val buf = ByteArray(1024)
            do {
                val toRead = if (buf.size < left) buf.size else left.toInt()
                readAll(buf, 0, toRead)
                left -= toRead
            } while (left > 0)
        }
    }
}
