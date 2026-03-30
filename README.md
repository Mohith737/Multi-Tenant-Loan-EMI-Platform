# Multi Tenant Loan EMI Platform
Multi-tenant loan origination and EMI management platform covering onboarding, underwriting, disbursement, and repayment flows.

## Overview
Multi Tenant Loan EMI Platform is a codebase focused on delivering the workflows and services represented in this repository. It combines the project modules found in the source tree into a single implementation for development, testing, and deployment. The repository is organized to support maintainable product development with clear separation between application layers and supporting assets.

## Tech Stack
- Docker
- Docker Compose
- Java
- PostgreSQL
- Spring Boot

## Features
- Authentication workflows
- Operational dashboards
- Data management APIs
- Automated testing support
- Environment-based configuration
- Modular frontend and backend structure

## Getting Started
### Prerequisites
Java 17+
Maven or the included Maven Wrapper
Docker

### Installation
Build Java services with `./mvnw clean install` or `mvn clean install` in each Spring Boot module.

### Environment Variables
Copy .env.example to .env and provide the values required for your local environment before starting the application.

### Running the Project
Run Spring Boot services with `./mvnw spring-boot:run` in the relevant module.
Use `docker compose up --build` when containerized services are provided.

## Project Structure
- Multi-Tenant-Loan-Origination-EMI-Management-Platform-main/
- .env
- .env.example
- .gitignore

## License
MIT
