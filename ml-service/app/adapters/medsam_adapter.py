from app.model_registry import ModelConfig


class MedSamAdapter:
    def __init__(self, config: ModelConfig):
        self.config = config

    def interactive_available(self) -> bool:
        return self.config.enabled
