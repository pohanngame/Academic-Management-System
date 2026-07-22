import os
import tempfile

from fastapi import FastAPI, File, UploadFile
import fitz
from paddleocr import PaddleOCR

app = FastAPI()
ocr = PaddleOCR(
    use_angle_cls=True,
    lang="ch",
    enable_mkldnn=False,
)


@app.post("/ocr")
async def recognize(file: UploadFile = File(...)):
    suffix = os.path.splitext(file.filename or "")[1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp_file:
        temp_file.write(await file.read())
        temp_path = temp_file.name
    try:
        lines = recognize_pdf(temp_path) if suffix.lower() == ".pdf" else recognize_image(temp_path)
        return {"text": "\n".join(lines)}
    finally:
        os.remove(temp_path)


def recognize_pdf(path):
    lines = []
    doc = fitz.open(path)
    try:
        for page in doc:
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            with tempfile.NamedTemporaryFile(delete=False, suffix=".png") as image_file:
                pixmap.save(image_file.name)
                image_path = image_file.name
            try:
                lines.extend(recognize_image(image_path))
            finally:
                os.remove(image_path)
    finally:
        doc.close()
    return lines


def recognize_image(path):
    result = ocr.predict(path)
    lines = []
    for page in result or []:
        for text in page.get("rec_texts", []) or []:
            if text:
                lines.append(text)
    return lines
