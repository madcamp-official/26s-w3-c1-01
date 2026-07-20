# assets — 모델 파일을 여기에 넣어야 한다

이 폴더에 **`face_landmarker.task`** 파일이 있어야 앱이 동작한다.
없으면 실행 즉시 `MODEL_LOAD_FAILED` 오류가 뜨고 포인터가 움직이지 않는다.

바이너리 파일이라 저장소에 커밋하지 않았다. 아래에서 직접 받아 이 폴더에 둘 것:

```
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
```

- 약 3.7MB
- 파일명을 바꾸면 안 된다 (`FaceTracker.MODEL_ASSET` 상수와 일치해야 함)
- 이 모델은 홍채 랜드마크(468~477)를 포함한다. **홍채가 없는 다른 모델로 바꾸면
  시선 보조 입력이 조용히 0이 된다** — 에러 없이 기능만 사라지므로 주의.

`app/build.gradle.kts`에 `noCompress += "task"`가 있는 이유도 이 파일 때문이다.
압축되면 MediaPipe가 메모리 매핑에 실패한다.
