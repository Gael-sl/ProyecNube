FROM nginx:alpine

RUN apk add --no-cache openssl

# Generate self-signed certificate automatically
RUN mkdir -p /etc/nginx/certs && \
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout /etc/nginx/certs/nginx.key \
    -out /etc/nginx/certs/nginx.crt \
    -subj "/C=MX/ST=CDMX/L=CDMX/O=SistemasDistribuidos/CN=localhost"

# Copy configurations and public pages
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/index.html
