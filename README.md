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
[I haven't picked a license yet](https://choosealicense.com/no-license/), so

> If you find software that doesn’t have a license, that generally means you have no permission from the creators of
the software to use, modify, or share the software. Although a code host such as GitHub may allow you to view and
fork the code, this does not imply that you are permitted to use, modify, or share the software for any purpose.