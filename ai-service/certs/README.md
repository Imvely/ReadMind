# 사내 루트 CA 투입 위치

사내 프록시가 HTTPS를 가로채는(MITM) 환경에서는 컨테이너가 외부 HTTPS
(Gemini API·Cloudflare R2)에 붙을 때 인증서 검증에 실패한다
(`SSL: CERTIFICATE_VERIFY_FAILED`).

이 폴더에 **사내 루트 CA 인증서**를 `*.crt`(PEM/Base64) 로 넣고 ai-service 이미지를
재빌드하면, Dockerfile 이 자동으로 신뢰 번들에 추가한다(google-genai·boto3·requests 전부).

- 파일 예: `corp-root.crt`
- `.crt` 파일은 커밋하지 않는다(.gitignore). 이 폴더 자체는 `.gitkeep` 으로 유지.
- 넣지 않으면 빌드는 그대로 되지만 사내망에서 외부 HTTPS 호출은 실패할 수 있다.

추출/적용 방법은 저장소 루트 `deploy/CORP_CA_GUIDE.md` 참고.
