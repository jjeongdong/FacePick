-- PhotoStorageCleaner 가 파일을 지우기 전에 같은 저장 키를 쓰는 사진(지운 뒤 같은 파일을 다시 올린 사진)이 있는지 본다.
CREATE INDEX idx_photos_storage_key ON photos (storage_key);
