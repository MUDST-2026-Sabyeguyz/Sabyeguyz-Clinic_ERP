# Used by Start-Local.ps1 -HostBuild after building the production Vue bundle.
FROM nginx:stable-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80
