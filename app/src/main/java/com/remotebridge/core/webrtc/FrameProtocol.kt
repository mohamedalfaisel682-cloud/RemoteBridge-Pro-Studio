package com.remotebridge.core.webrtc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * بروتوكول تأطير الرسائل فوق قناة بيانات WebRTC.
 *
 * تنسيق الإطار (17 بايت رأس + حمولة):
 *   [1 بايت]  type       : 0 = تحكم JSON، 1 = قطعة ثنائية
 *   [4 بايت]  messageId  : معرّف الرسالة (لمطابقة الردود والملفات)
 *   [4 بايت]  seq        : رقم القطعة (يبدأ من 0)
 *   [4 بايت]  total      : إجمالي القطع (0 لرسائل التحكم أحادية)
 *   [4 بايت]  payloadLen : طول الحمولة
 *   [N بايت] payload
 *
 * القناة مرتبة (ordered = true) لذا تصل القطع بالترتيب نفسه المُرسل.
 */
object FrameProtocol {
    const val TYPE_JSON: Byte = 0
    const val TYPE_BINARY: Byte = 1

    const val HEADER_SIZE = 17

    /** حجم القطعة الواحدة — أقل من حد SCTP لضمان الوصول الموثوق. */
    const val MAX_PAYLOAD = 16 * 1024

    data class Frame(
        val type: Byte,
        val messageId: Int,
        val seq: Int,
        val total: Int,
        val payload: ByteBuffer
    )

    /**
     * يحلّل إطاراً وارداً. يعيد null إذا كان غير مكتمل.
     * الحمولة المعادة هي شريحة (slice) من المخزن الأصلي.
     */
    fun parse(buffer: ByteBuffer): Frame? {
        if (buffer.remaining() < HEADER_SIZE) return null
        buffer.order(ByteOrder.BIG_ENDIAN)
        val type = buffer.get()
        val messageId = buffer.int
        val seq = buffer.int
        val total = buffer.int
        val len = buffer.int
        if (len < 0 || buffer.remaining() < len) return null
        val payload = buffer.slice().order(ByteOrder.BIG_ENDIAN).limit(len) as ByteBuffer
        return Frame(type, messageId, seq, total, payload)
    }

    /**
     * يبني إطار تحكم JSON أحادي القطعة.
     */
    fun buildJsonFrame(messageId: Int, json: String): ByteBuffer {
        val payload = json.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(TYPE_JSON)
        buffer.putInt(messageId)
        buffer.putInt(0)
        buffer.putInt(0)
        buffer.putInt(payload.size)
        buffer.put(payload)
        buffer.flip()
        return buffer
    }

    /**
     * يبني قطعة ثنائية واحدة من ملف.
     */
    fun buildBinaryChunk(
        messageId: Int,
        seq: Int,
        total: Int,
        chunk: ByteArray,
        offset: Int,
        length: Int
    ): ByteBuffer {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + length)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.put(TYPE_BINARY)
        buffer.putInt(messageId)
        buffer.putInt(seq)
        buffer.putInt(total)
        buffer.putInt(length)
        buffer.put(chunk, offset, length)
        buffer.flip()
        return buffer
    }

    /**
     * يحسب عدد القطوع اللازمة لملف بحجم معين (لا يقل عن 1).
     */
    fun totalChunks(size: Long): Int {
        return ((size + MAX_PAYLOAD - 1) / MAX_PAYLOAD).toInt().coerceAtLeast(1)
    }
}
