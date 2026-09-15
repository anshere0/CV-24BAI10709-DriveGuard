FROM python:3.10-slim

WORKDIR /app

RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    gcc \
    python3-dev \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy cv_engine, backend, and main app files
COPY cv_engine/ /app/cv_engine/
COPY backend/ /app/backend/
COPY models/ /app/models/
COPY config.yaml /app/config.yaml
COPY main.py /app/main.py

CMD ["uvicorn", "backend.app.main:app", "--host", "0.0.0.0", "--port", "8000"]
