FROM postgres:16-alpine

RUN apk add --no-cache bash

COPY standby-entrypoint.sh /usr/local/bin/standby-entrypoint.sh
RUN chmod +x /usr/local/bin/standby-entrypoint.sh

# Change entrypoint to run standby script as the postgres user
USER postgres

ENTRYPOINT ["/usr/local/bin/standby-entrypoint.sh"]
