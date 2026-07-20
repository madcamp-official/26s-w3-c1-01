package com.madcamp.handsfree.integration

import android.content.Context
import android.util.Log
import com.mobileconductor.core.model.CalibrationProfile
import com.mobileconductor.core.model.FaceOrientationValue
import com.mobileconductor.core.model.Level

/**
 * 캘리브레이션 프로파일 로컬 저장소.
 *
 * **통합에서 처음 만든 것이다.** 명세(NFR)에 "캘리브레이션 수치만 로컬에 남긴다"가
 * 있고 D의 [CalibrationProfile] 주석에도 "기기 로컬 저장"이라고 적혀 있는데 구현한
 * 사람이 없었다 — A는 "저장은 D 담당", D는 프로파일 생성까지만 해서 파트 경계
 * 사이로 빠졌다. 저장이 없으면 앱을 껐다 켤 때마다 22초짜리 보정을 다시 해야 한다.
 *
 * **얼굴 이미지나 음성은 저장하지 않는다.** 여기 남는 건 각도 범위와 레벨 숫자뿐이고,
 * 그게 NFR(온디바이스·오프라인)이 허용하는 유일한 저장 대상이다.
 * 이 파일에 다른 걸 추가하려 한다면 그 요구사항을 깨는 것이다.
 */
object CalibrationStore {

    /**
     * 프로파일은 **모드별로 따로 저장한다**(Phase 3). FACE의 얼굴 각도 범위와 HAND의 손
     * 도달 범위는 의미가 완전히 달라서 한 저장소를 공유하면 안 된다. prefs 파일명을
     * 모드로 분리한다: `calibration_face` / `calibration_hand`.
     */
    private const val PREFS = "calibration"

    /** 마지막으로 선택한 입력 모드. 재시작 시 같은 모드로 부팅한다. */
    private const val MODE_PREFS = "input_mode"
    private const val KEY_LAST_MODE = "lastMode"

    /**
     * 저장 형식/각도 규약 버전.
     *
     * **yaw·pitch의 의미가 바뀌면 반드시 올릴 것.** 저장된 숫자는 그대로인데
     * 해석이 바뀌면 앱은 아무 경고 없이 엉뚱한 범위로 매핑한다 — 사용자 눈에는
     * "고쳤다는데 그대로네"로 보인다.
     *
     * v2: HeadPose를 오일러 분해에서 forward 벡터 방식으로 바꾸면서 pitch 부호가
     *     뒤집혔다. v1로 저장된 프로파일은 쓸 수 없다.
     */
    private const val VERSION = 2
    private const val KEY_VERSION = "schemaVersion"
    private const val KEY_ID = "profileId"
    private const val KEY_POINTS = "referencePoints"
    private const val KEY_YAW_MIN = "faceRangeYawMin"
    private const val KEY_YAW_MAX = "faceRangeYawMax"
    private const val KEY_PITCH_MIN = "faceRangePitchMin"
    private const val KEY_PITCH_MAX = "faceRangePitchMax"
    private const val KEY_SENSITIVITY = "sensitivityLevel"
    private const val KEY_SMOOTHING = "smoothingLevel"
    private const val KEY_CREATED = "createdAt"
    private const val KEY_UPDATED = "updatedAt"

    private const val TAG = "HF-CalibStore"

    fun save(context: Context, mode: InputMode, profile: CalibrationProfile) {
        prefs(context, mode).edit()
            .putInt(KEY_VERSION, VERSION)
            .putString(KEY_ID, profile.profileId)
            .putString(KEY_POINTS, encodePoints(profile.referencePoints))
            .putFloat(KEY_YAW_MIN, profile.faceRangeYawMin)
            .putFloat(KEY_YAW_MAX, profile.faceRangeYawMax)
            .putFloat(KEY_PITCH_MIN, profile.faceRangePitchMin)
            .putFloat(KEY_PITCH_MAX, profile.faceRangePitchMax)
            .putString(KEY_SENSITIVITY, profile.sensitivityLevel.name)
            .putString(KEY_SMOOTHING, profile.smoothingLevel.name)
            .putString(KEY_CREATED, profile.createdAt)
            .putString(KEY_UPDATED, profile.updatedAt)
            .apply()
        Log.i(TAG, "프로파일 저장 — ${profile.profileId} ($mode)")
    }

    /**
     * @return 저장된 프로파일. 없거나 형식이 깨졌으면 null.
     *
     * 깨진 값으로 매핑하면 포인터가 화면 구석에 처박혀서 "앱이 고장 났다"로 보인다.
     * 그럴 바에는 재보정을 시키는 게 낫다.
     */
    fun load(context: Context, mode: InputMode): CalibrationProfile? {
        val p = prefs(context, mode)
        val version = p.getInt(KEY_VERSION, 0)
        if (version != VERSION) {
            Log.i(TAG, "각도 규약이 바뀌어 저장된 프로파일을 버린다 (v$version → v$VERSION, $mode) — 재보정 필요")
            clear(context, mode)
            return null
        }
        val id = p.getString(KEY_ID, null) ?: return null
        return try {
            val points = decodePoints(p.getString(KEY_POINTS, "") ?: "")
            CalibrationProfile(
                profileId = id,
                referencePoints = points,
                faceRangeYawMin = p.getFloat(KEY_YAW_MIN, 0f),
                faceRangeYawMax = p.getFloat(KEY_YAW_MAX, 0f),
                faceRangePitchMin = p.getFloat(KEY_PITCH_MIN, 0f),
                faceRangePitchMax = p.getFloat(KEY_PITCH_MAX, 0f),
                sensitivityLevel = Level.valueOf(p.getString(KEY_SENSITIVITY, null) ?: Level.MID.name),
                smoothingLevel = Level.valueOf(p.getString(KEY_SMOOTHING, null) ?: Level.MID.name),
                createdAt = p.getString(KEY_CREATED, "") ?: "",
                updatedAt = p.getString(KEY_UPDATED, "") ?: "",
            )
        } catch (e: Exception) {
            // referencePoints가 9개가 아니면 CalibrationProfile의 init require가 던진다.
            Log.w(TAG, "저장된 프로파일을 읽지 못해 폐기한다 ($mode) — 재보정 필요", e)
            clear(context, mode)
            null
        }
    }

    fun clear(context: Context, mode: InputMode) = prefs(context, mode).edit().clear().apply()

    private fun prefs(context: Context, mode: InputMode) =
        context.getSharedPreferences("${PREFS}_${mode.name.lowercase()}", Context.MODE_PRIVATE)

    // ── 마지막 선택 모드 영속화 ───────────────────────────────

    fun saveMode(context: Context, mode: InputMode) {
        context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_MODE, mode.name).apply()
    }

    fun loadMode(context: Context): InputMode {
        val name = context.getSharedPreferences(MODE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_MODE, null) ?: return InputMode.FACE
        return runCatching { InputMode.valueOf(name) }.getOrDefault(InputMode.FACE)
    }

    private fun encodePoints(points: List<FaceOrientationValue>): String =
        points.joinToString(";") { "${it.yaw}:${it.pitch}" }

    private fun decodePoints(raw: String): List<FaceOrientationValue> =
        raw.split(";").filter { it.isNotBlank() }.map {
            val (yaw, pitch) = it.split(":")
            FaceOrientationValue(yaw.toFloat(), pitch.toFloat())
        }
}
