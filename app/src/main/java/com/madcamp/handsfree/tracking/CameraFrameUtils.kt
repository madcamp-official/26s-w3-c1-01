package com.madcamp.handsfree.tracking

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * CameraX 프레임 변환 공용 유틸.
 *
 * 원래 [FaceTracker.kt]에 파일 레벨 private로 있던 것을 **로직 그대로** 옮겼다.
 * FACE(FaceTracker)와 HAND(HandTracker)가 같은 전면 카메라 프레임을 같은 방식으로
 * 다뤄야 해서(같은 회전 보정·같은 저조도 판정), 한 곳에 두고 둘 다 쓴다.
 *
 * **여기서 좌우 미러링은 하지 않는다.** 미러링 규약(사용자 오른쪽 → x 증가)은 각
 * 트래커가 각도/좌표 단계에서 부호로 흡수한다(OPEN_ISSUES #5 / MOTION_CAPTURE_SPEC §11-1).
 */

/** ImageProxy(RGBA_8888) → 회전 보정된 Bitmap */
internal fun ImageProxy.toUprightBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width

    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888,
    )
    buffer.rewind()
    bitmap.copyPixelsFromBuffer(buffer)

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
}

/**
 * 프레임 평균 휘도로 저조도를 판정한다.
 *
 * 명세서는 저조도를 confidence에 섞으라고 했지만 둘은 다른 신호다 —
 * 어두워도 검출은 잘 되고, 밝아도 얼굴이 기울면 검출이 나빠진다 (OPEN_ISSUES #4).
 */
internal fun Bitmap.isLowLight(): Boolean {
    // 전체 픽셀을 훑으면 프레임마다 수십만 번 연산이라 fps가 깎인다. 격자로 표본만 본다
    val step = 16
    var sum = 0L
    var count = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val p = getPixel(x, y)
            // 정확한 luma 대신 근사. 저조도 판정에 정밀도는 필요 없다
            sum += ((p shr 16 and 0xFF) * 3 + (p shr 8 and 0xFF) * 6 + (p and 0xFF)) / 10
            count++
            x += step
        }
        y += step
    }
    if (count == 0) return false
    return sum / count < LOW_LIGHT_LUMA
}

private const val LOW_LIGHT_LUMA = 60
