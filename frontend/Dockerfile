# ใช้ Nginx สำหรับเสิร์ฟไฟล์ Static ได้เลยโดยตรง (ไม่ต้องรัน Node.js บนเซิร์ฟเวอร์)
FROM nginx:stable-alpine

# ก๊อปปี้โฟลเดอร์ dist ที่ Build เสร็จแล้วจากเครื่องเรา เข้าไปใน Nginx
COPY dist /usr/share/nginx/html

# ก๊อปปี้ไฟล์คอนฟิก Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]