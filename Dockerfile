# Copyright 2025 Logan Magee
#
# SPDX-License-Identifier: AGPL-3.0-only

FROM ghcr.io/graalvm/native-image-community:24 AS builder

# findutils is needed because it includes xargs, a dependency of gradlew
RUN microdnf install findutils
COPY . .
RUN ./gradlew assemble --no-daemon -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false

FROM quay.io/quarkus/quarkus-distroless-image:2.0

COPY --from=builder /app/directory/build/directory-*-runner directory-server
USER nobody

EXPOSE 8080

ENTRYPOINT ["./directory-server"]
