# Testing

The frontend build is verified with `npm run build`. Backend verification is `mvn test`; in this environment it is currently blocked before compilation because Maven dependencies cannot be downloaded (network DNS is unavailable and the default Maven cache is permission-restricted). The project includes an H2 test profile for application tests once dependencies are available.
