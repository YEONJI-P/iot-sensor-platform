FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 실측 CSV 리플레이 배치. data/ 는 볼륨으로 마운트하고, 실행 인자는 compose command 로 전달.
ENTRYPOINT ["python", "simulator.py"]
