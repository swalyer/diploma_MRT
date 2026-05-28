FROM python:3.11-slim AS base
WORKDIR /app
ENV PYTHONPATH=/app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

FROM base AS runtime
COPY app ./app
COPY config ./config
EXPOSE 8000
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000"]

FROM base AS test
COPY requirements-dev.txt .
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY app ./app
COPY config ./config
COPY tests ./tests
COPY contracts ./contracts
COPY scripts ./scripts
CMD ["pytest","-q"]
