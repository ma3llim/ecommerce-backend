# E-Commerce Backend

A production-oriented **E-Commerce REST API** built with Java and Spring Boot.

The backend follows a **modular monolith architecture**, where business functionality is organized into independent modules while keeping the application simple to develop, deploy, and maintain.

The application provides authentication, product catalog management, shopping cart functionality, order processing, payments, coupons, reviews, email notifications, API documentation, rate limiting, monitoring, and production deployment support.

## Overview

The E-Commerce Backend provides the core business APIs required by the E-Commerce application.

## Features

- Secure user authentication & authorization (Registration, Login, Logout, Email Verification, Password Reset, JWT)
- User profile and address management
- Product catalog management with categories, variants, images, search, and filtering
- Product reviews and ratings
- Shopping cart management
- Order management and order lifecycle handling
- Coupon management and discount validation
- Secure payment processing via Razorpay with webhook and refund handling
- Transactional email notifications for authentication, orders, payments, and refunds
- API rate limiting using Bucket4j
- Application monitoring and observability using Actuator, Micrometer, Prometheus, and Grafana
- RESTful APIs documented using OpenAPI / Swagger
- Role-based access control for users and administrators

## Technology Stack

- **Language:** Java 21
- **Framework:** Spring Boot
- **Build Tool:** Gradle
- **Security:** Spring Security
- **Authentication:** JWT
- **Database:** PostgreSQL
- **Production Database:** Neon PostgreSQL
- **Payment Gateway:** Razorpay
- **Email Templates:** Thymeleaf
- **API Documentation:** OpenAPI / Swagger
- **Rate Limiting:** Bucket4j
- **Monitoring:** Spring Boot Actuator
- **Metrics:** Micrometer
- **Metrics Collection:** Prometheus
- **Dashboards:** Grafana
- **Containerization:** Docker
- **Cloud:** AWS
- **Compute:** AWS EC2
- **Reverse Proxy:** Nginx
- **CI/CD:** GitHub Actions

## Architecture

The backend uses a **modular monolith** architecture.

```text
                    E-Commerce Backend
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
       Auth              User              Catalog
        │                  │                  │
        └──────────────┬───┴──────────────┬───┘
                       │                  │
                      Cart              Review
                        │                  │
                        └────────┬─────────┘
                                 │
                               Order
                                 │
                          ┌──────┴──────┐
                          │             │
                        Coupon        Payment
                                        │
                                        │
                                    Razorpay
```

## Installation

1. Clone the Repository

    ```bash
    git clone https://github.com/ma3llim/ecommerce-backend/
    cd ecommerce-backend
    ```

2. Set Up Environment Variables

    Take reference from the `.env.example` file and configure the required environment variables in your local environment or IntelliJ IDEA.

3. Build the Application

    ```bash
    ./gradlew clean build
    ```

    On Windows:

    ```bash
    gradlew.bat clean build
    ```

4. Run the Application

    ```bash
    ./gradlew bootRun
    ```

## API Documentation

The backend uses **OpenAPI / Swagger** for API documentation.

The API documentation is organized into appropriate API groups, including:

- user-apis
- admin-apis

Once the application is running, access the Swagger UI using the configured Swagger endpoint.

## Deployment

The production architecture separates the frontend, backend, and database.

```text
                         Internet
                            │
                            ▼
                      ┌───────────┐
                      │  Frontend │
                      │  Vercel   │
                      └─────┬─────┘
                            │
                            │ HTTPS / REST API
                            ▼
                      ┌───────────┐
                      │   Nginx   │
                      └─────┬─────┘
                            │
                            ▼
                      ┌───────────────┐
                      │    AWS EC2    │
                      │               │
                      │    Docker     │
                      │       │       │
                      │  Spring Boot  │
                      └───────┬───────┘
                              │
                         ┌────┴────┐
                         ▼         ▼
                   PostgreSQL    Redis
                      Neon      (Planned)
```

## CI/CD

The backend is designed to use GitHub Actions for continuous integration and deployment.

The deployment flow is:

```text
Developer
    │
    ▼
GitHub
    │
    ▼
GitHub Actions
    │
    ├── Security Checks
    ├── Build
    └── Tests / Checks
    │
    ▼
Docker Image
    │
    ▼
Container Registry
    │
    ▼
AWS EC2
    │
    ▼
Docker Container
    │
    ▼
Health Check
    │
    ▼
Deployment Complete
```

The project uses a practical single-production-environment deployment approach rather than introducing unnecessary multi-environment infrastructure.

## License

This project is provided for educational and portfolio purposes.

See the [LICENSE](LICENSE) file in the repository for the applicable license terms.

## Acknowledgements

- **Spring Boot:** Robust framework for building the backend application.
- **Spring Security:** Secure authentication and authorization.
- **PostgreSQL & Neon:** Reliable relational database and managed PostgreSQL - infrastructure.
- **Razorpay:** Secure and seamless payment processing.
- **Thymeleaf:** Dynamic and responsive email templates.
- **Bucket4j:** API rate limiting and request protection.
- **Prometheus & Grafana:** Application monitoring and metrics visualization.
- **Docker & AWS:** Containerization and cloud deployment.
- **GitHub Actions:** Continuous integration and deployment automation.
