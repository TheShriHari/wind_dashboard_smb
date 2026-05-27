FROM python:3.12-slim

# System dependencies for asyncpg (C extension) compilation
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Install uv for fast dependency installation
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

WORKDIR /app

# Copy only the package manifest first (layer cache: deps only reinstall on
# pyproject.toml changes, not every source change)
COPY Backend/pyproject.toml ./Backend/pyproject.toml

# Install all dependencies into the system Python using uv
RUN uv pip install --system --no-cache \
    fastapi uvicorn[standard] sqlmodel asyncpg sqlalchemy[asyncio] \
    httpx beautifulsoup4 lxml apscheduler alembic python-dotenv pydantic

# Copy the full project (Backend/, scripts/, alembic/, etc.)
COPY . .

# Create logs directory
RUN mkdir -p /app/Backend/logs /app/logs

# Non-root user for security
RUN adduser --disabled-password --gecos "" appuser && \
    chown -R appuser:appuser /app
USER appuser

EXPOSE 8000

# Backend.main:app works because /app/Backend/ exists as a package
CMD ["uvicorn", "Backend.main:app", "--host", "0.0.0.0", "--port", "8000"]
