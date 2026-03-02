from fastapi import FastAPI
from app.api.routes import router

app = FastAPI(title='mrt-ml-service')
app.include_router(router)
