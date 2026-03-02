import os

class Settings:
    mode = os.getenv("ML_MODE", "mock")
    artifacts_root = os.getenv("ARTIFACTS_ROOT", "./storage/ml")

settings = Settings()
