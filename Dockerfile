# syntax=docker/dockerfile:1

FROM golang

WORKDIR /code

COPY go.mod ./
COPY go.sum ./
RUN go mod download

COPY client.go ./

RUN go build -o /docker-gs-ping

EXPOSE 5000


CMD [ "/docker-gs-ping" ]