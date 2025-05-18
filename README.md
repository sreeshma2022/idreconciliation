# BiteSpeed ID Reconciliation Service

A Java Spring Boot application that identifies and merges contacts based on email and phone number, maintaining a consistent primary and secondary contact hierarchy.

## Features

- Create and manage primary and secondary contacts
- Automatically merge contacts with matching email or phone numbers
- REST API to identify or add contacts
- Uses H2 database
- Dockerized for easy deployment and scalability

## Technology Stack

- Java 17
- Spring Boot
- Maven
- Docker
- H2 database
- JUnit & Mockito for testing

## Getting Started

### Prerequisites

- Java 17 SDK
- Maven 3.6+
- Docker (optional, for containerization)
- Git

### Build and Run Locally

#### Clone the repository

```bash
git clone https://github.com/sreeshma2022/idreconciliation.git
cd idreconciliation
```

#### Build the project and package the JAR

  ```bash
      mvn clean package
  ```
#### Run the application locally
```bash
java -jar target/idreconciliation-0.0.1-SNAPSHOT.jar
```
- The application will start and listen on http://localhost:8080.

### Using Docker

#### Build Docker image
```bash
   docker build -t idreconciliation .
```
#### Run Docker container

```bash
 docker run -p 8080:8080 idreconciliation
```

### API USAGE
 - Use POST /identify (or your endpoint)
 - Use this endpoint with email and/or phoneNumber to identify or add contacts.
  #### Request body example:
   ``` bash
     {
  "email": "example@example.com",
  "phoneNumber": "1234567890"
}
   ```
#### Sample response:
 ``` bash
    {
  "primaryContactId": 11,
  "emails": ["example@example.com", "other@example.com"],
  "phoneNumbers": ["1234567890", "0987654321"],
  "secondaryContactIds": [27, 33]
}
```

### Notes
 - Configure your database connection in src/main/resources/application.yml.
 - The application uses in-memory H2 database by default for quick testing.

