# MoarSMS

Fortumo [PSMS API](https://merchants.fortumo.com/integration-and-testing/api/) handler for Bukkit

## Requirements
- CraftBukkit/Spigot 1.11.2+ (Paper is preferred)
- One free port for HTTP requests. Preferably hide it behind reverse proxy with HTTPS (for example [nginx](https://nginx.org/en/))

## Building
Invoke `./gradlew build` and grab jar from `build/libs/`

## Configuration
I made it easy enough to use. If you can't figure out how to use it, then you are on your own :^)

### Reverse proxy
nginx example:
```
location / {
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Nginx-Proxy "true"
    proxy_pass http://<server-ip>:<port>;
    proxy_redirect off;
    proxy_http_version 1.1;
}
```

## License
MIT