FROM python:3.12-slim
WORKDIR /app
COPY server.py .
ENV PORT=8000 KAKAS_DB=/data/kakas.db PYTHONUNBUFFERED=1
VOLUME /data
EXPOSE 8000
CMD ["python3", "server.py"]
