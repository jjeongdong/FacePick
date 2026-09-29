"""측정용 사진 준비: source/ 의 JPEG 뒤에 무작위 바이트를 붙여 해시만 다른 파일을 만든다.

백엔드 저장 키가 내용 해시라 한 앨범에 같은 파일을 두 번 올리면 다시 처리되지 않는다.
JPEG 디코더는 EOI(FF D9) 뒤 바이트를 무시하므로 이미지와 처리 비용은 원본과 같다.

사용법: python3 prepare_photos.py 100
"""

import hashlib
import json
import secrets
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).parent
SOURCE = ROOT / "source"
OUTPUT = ROOT / "photos"


def main() -> None:
    count = int(sys.argv[1])
    sources = sorted(p for p in SOURCE.iterdir() if p.suffix.lower() in (".jpg", ".jpeg"))
    if not sources:
        sys.exit(f"{SOURCE} 에 JPEG 가 없습니다")
    shutil.rmtree(OUTPUT, ignore_errors=True)
    OUTPUT.mkdir()
    manifest = []
    for index in range(count):
        source = sources[index % len(sources)]
        data = source.read_bytes()
        if not data.startswith(b"\xff\xd8"):
            sys.exit(f"JPEG 가 아닙니다: {source.name}")
        data += secrets.token_bytes(16)
        name = f"{index:04d}.jpg"
        (OUTPUT / name).write_bytes(data)
        manifest.append(
            {"file": name, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
        )
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print(f"{count}장 생성 (원본 {len(sources)}장)")


if __name__ == "__main__":
    main()
