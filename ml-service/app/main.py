from fastapi import FastAPI
from app.api.routes import router
from app.observability import setup_metrics

app = FastAPI(title='mrt-ml-service')
setup_metrics(app)
app.include_router(router)
