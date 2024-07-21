# Use an official OpenJDK runtime as a parent image
FROM openjdk:11

# Set environment variables for Scala and sbt versions
ENV SCALA_VERSION 2.13.8
ENV SBT_VERSION 1.5.5

# Install Scala
RUN \
    curl -fsL "https://downloads.lightbend.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz" | tar xfz - -C /usr/local/

# Install sbt
RUN \
    curl -fsL "https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz" | tar xfz - -C /usr/local/

# Set PATH environment variable for sbt
ENV PATH="/usr/local/sbt/bin:${PATH}"

# Copy the project files into the container
WORKDIR /app
COPY . .

# Compile the Scala project
RUN sbt compile

# Expose any necessary ports
# Replace 9000 with your application's port number
EXPOSE 8083

# Command to run the application
CMD ["sbt", "run"]
