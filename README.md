# Liver MRI/CT Analysis MVP

Production-like MVP for liver MRI/CT decision support:
- `backend/`: Java Spring Boot REST API
- `frontend/`: React + TypeScript UI
- `ml-service/`: FastAPI mock/real-ready inference pipeline
- `database/`: DB artifacts and sample data
- `storage/`: local artifact storage

## Run
```bash
docker compose up --build
```

Services:
- Frontend: http://localhost:5173
- Backend Swagger: http://localhost:8080/swagger-ui/index.html
- ML service docs: http://localhost:8000/docs

## Default demo user
- email: `admin@demo.local`
- password: `Admin123!`

## API examples
Examples for case with lesion and without lesion are in `database/api-examples.json`.

## Notes
System is decision-support only and requires physician verification.
