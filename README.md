# Content

  * [Content](#content)
  * [Consense](#consense)
    * [Requirements](#requirements)
    * [Installation](#installation)
      * [Docker Image](#docker-image)
      * [Executable](#executable)
  * [Running Consense](#running-consense)
      * [Docker Compose](#with-docker-compose)
      * [Startup Script](#with-startup-script)

# Consense
Distributed consensus simulation and visualization framework.

## Requirements
* [Java 8](https://openjdk.java.net/install/)
* [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)
* [yarn](https://classic.yarnpkg.com/en/docs/install)
* [Docker](https://docs.docker.com/get-docker/) (if building and running the Consense image)

## Installation

### Docker Image
To build a Docker image for Consense and publish it to your local repository, run `sbt docker:publishLocal` in the project root directory.

### Executable
To build a standalone executable for Consense, run `sbt stage` in the project. Scripts for Linux and Windows systems will be generated in `target/universal/stage/bin`. **Note: the generated scripts are __NOT__ portable.** 

# Running Consense

## With Docker Compose
A `docker-compose.yml` file exists in the root directory. If Docker Compose is installed, the Consense webapp can be started with `docker-compose up`. This will start and expose the webapp on port 8080.

## With Startup Script
The `target/universal/stage/bin/consense` script can be used to start the webapp after it has been fully staged with `sbt stage`.
