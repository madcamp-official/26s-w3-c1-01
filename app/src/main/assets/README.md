# assets — 모델 파일

이 폴더의 **`face_landmarker.task`**(얼굴/시선, FACE 모드)와
**`hand_landmarker.task`**(손, HAND 모드)가 있어야 앱이 동작한다.
없으면 실행 즉시 `MODEL_LOAD_FAILED` 오류가 뜨고 해당 모드 포인터가 움직이지 않는다.

**두 파일 모두 저장소에 포함돼 있다.** 클론하면 바로 있으니 따로 받을 필요가 없다.
(통합 전에는 gitignore로 제외했었는데, 클론한 팀원이 `MODEL_LOAD_FAILED`만 보고
원인을 알 수 없어서 포함하는 쪽으로 바꿨다. 합쳐서 ~11MB는 git이 감당할 크기다.)

## face_landmarker.task (FACE 모드)

원본 출처 — 손상되거나 갱신이 필요할 때만 쓴다:

```
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
```

- 약 3.7MB
- 파일명을 바꾸면 안 된다 (`FaceTracker.MODEL_ASSET` 상수와 일치해야 함)
- 이 모델은 홍채 랜드마크(468~477)를 포함한다. **홍채가 없는 다른 모델로 바꾸면
  시선 보조 입력이 조용히 0이 된다** — 에러 없이 기능만 사라지므로 주의.

## hand_landmarker.task (HAND 모드 — 모션 캡쳐)

원본 출처:

```
https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
```

- 약 7.5MB
- 파일명을 바꾸면 안 된다 (`HandTracker.MODEL_ASSET` 상수와 일치해야 함)
- 손 랜드마크 21점을 준다. 포인터 기준점은 검지 끝(#8), 핀치 판정은 엄지 끝(#4)·검지 끝(#8) 거리.

`app/build.gradle.kts`에 `noCompress += "task"`가 있는 이유도 이 파일 때문이다.
압축되면 MediaPipe가 메모리 매핑에 실패한다.
