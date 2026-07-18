FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# replay는 data/를 볼륨으로 받고, synthetic은 파일 없이 실행한다. 모드와 정책은 compose command로 전달한다.
ENV PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "simulator.py"]
