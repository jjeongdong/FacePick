class PermanentError(Exception):
    """다시 시도해도 결과가 같은 실패. 워커 루프가 DLQ 로 보낸다."""

    def __init__(self, error_type: str, message: str):
        super().__init__(message)
        self.error_type = error_type
